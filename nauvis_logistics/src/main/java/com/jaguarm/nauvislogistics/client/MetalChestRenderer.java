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

/** Vanilla's chest renderer, pointed at our own sprites. */
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
