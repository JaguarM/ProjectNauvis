package com.jaguarm.nauvisrocket.silo;

import java.util.List;

import com.jaguarm.nauvisrocket.NauvisRocket;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;

/**
 * The launch: what a rocket leaving the pad looks and sounds like, and what it means.
 *
 * <p>Factorio's launch is a forty-second animation of doors, a gantry and a rocket climbing out
 * of frame; this pack's silo is block models and has no animation to give. So the launch is
 * smoke and fire at the base of the rocket for the length of the countdown, and then a firework
 * of the rocket's own colour climbing off the nose and bursting high over the base - the one
 * thing Minecraft already has that goes up and is seen from across a factory. The rocket itself
 * stays where it is drawn, because it is drawn whether or not one has been built; see
 * {@code GAPS.md}.
 *
 * <p>What it means is the game's end: {@link #ADVANCEMENT} for every player on the server, in
 * the challenge frame with the sound that goes with it, a title on every screen, and a line in
 * chat. The science it sends back is the silo's business.
 */
public final class Launch {

    private Launch() {}

    /** Awarded to every player the moment a rocket leaves. The criterion is impossible; the silo grants it. */
    public static final Identifier ADVANCEMENT = Identifier.fromNamespaceAndPath(NauvisRocket.MODID, "launch");
    public static final String CRITERION = "launched";

    /** Factorio's rocket, seen from below: white, and a red nose. */
    private static final int WHITE = 0xF0F0F0;
    private static final int RED = 0xC83C3C;

    /** Fire and smoke under the rocket. Called every few ticks of the countdown, at the deck's middle. */
    public static void exhaust(ServerLevel level, Vec3 base) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, base.x, base.y + 0.2, base.z, 12, 1.2, 0.2, 1.2, 0.02);
        level.sendParticles(ParticleTypes.FLAME, base.x, base.y + 0.2, base.z, 6, 0.6, 0.3, 0.6, 0.05);
    }

    /** The countdown's first tick: the engines lighting. */
    public static void ignite(ServerLevel level, Vec3 base) {
        level.playSound(null, base.x, base.y, base.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 4.0F, 0.4F);
        level.sendParticles(ParticleTypes.EXPLOSION, base.x, base.y + 0.5, base.z, 3, 0.8, 0.2, 0.8, 0.0);
    }

    /** The countdown's last tick: the rocket leaving the nose, as a firework that climbs and bursts. */
    public static void liftOff(ServerLevel level, Vec3 nose) {
        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);
        firework.set(DataComponents.FIREWORKS, new Fireworks(3, List.of(
                new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL, IntList.of(WHITE, RED), IntList.of(WHITE),
                        true, true))));
        FireworkRocketEntity rocket = new FireworkRocketEntity(level, nose.x, nose.y, nose.z, firework);
        rocket.setDeltaMovement(0.0, 1.2, 0.0);
        level.addFreshEntity(rocket);
        level.playSound(null, nose.x, nose.y, nose.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.BLOCKS,
                4.0F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, nose.x, nose.y - 4, nose.z, 1, 0, 0, 0, 0);
    }

    /** The game's end, told to everybody: the advancement, a title and a line in chat. */
    public static void celebrate(MinecraftServer server, BlockPos silo) {
        Component title = Component.translatable("message.nauvis_rocket.launched");
        Component subtitle = Component.translatable("message.nauvis_rocket.launched.where",
                silo.getX(), silo.getY(), silo.getZ());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            award(player);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.nauvis_rocket.launched.chat", silo.getX(), silo.getY(), silo.getZ()),
                false);
    }

    /**
     * The advancement to one player. Idempotent: an award already made is a no-op, and a missing
     * advancement - a datapack that removed it - is skipped in silence rather than a crash mid-launch.
     *
     * @return whether the player has it now
     */
    public static boolean award(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        AdvancementHolder holder = server.getAdvancements().get(ADVANCEMENT);
        if (holder == null) {
            return false;
        }
        player.getAdvancements().award(holder, CRITERION);
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
