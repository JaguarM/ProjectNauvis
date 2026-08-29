package com.jaguarm.nauvislogistics.client;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.storage.MetalChestBlockEntity;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

/**
 * Vanilla's chest renderer, pointed at our own sprites.
 *
 * <p>Nothing about the drawing is ours: the model, the lid's swing and the easing curve on it are
 * {@link ChestRenderer}'s. All that is overridden is which sprite the model is painted with, which
 * NeoForge added {@code getCustomSprite} for - without it a modded chest is drawn as an oak one
 * and there is no other hook that would change it.
 *
 * <p>The sprite lives in vanilla's chest atlas. That is not a special arrangement: the atlas is
 * built from a {@code minecraft:directory} source over {@code textures/entity/chest}, and a
 * directory source scans every namespace, so a PNG at
 * {@code assets/nauvis_logistics/textures/entity/chest/iron.png} is stitched in with no atlas
 * file of our own. {@code Sheets.CHEST_MAPPER} is the same object vanilla uses to name its own.
 */
public class MetalChestRenderer extends ChestRenderer<MetalChestBlockEntity> {

    /** Named for the tier's block, so a third chest would be one more line and one more PNG. */
    private final SpriteId sprite;

    private MetalChestRenderer(BlockEntityRendererProvider.Context context, String texture) {
        super(context);
        this.sprite = Sheets.CHEST_MAPPER.apply(
                Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, texture));
    }

    public static MetalChestRenderer iron(BlockEntityRendererProvider.Context context) {
        return new MetalChestRenderer(context, "iron");
    }

    public static MetalChestRenderer steel(BlockEntityRendererProvider.Context context) {
        return new MetalChestRenderer(context, "steel");
    }

    @Override
    protected @Nullable SpriteId getCustomSprite(MetalChestBlockEntity chest, ChestRenderState state) {
        return sprite;
    }
}
