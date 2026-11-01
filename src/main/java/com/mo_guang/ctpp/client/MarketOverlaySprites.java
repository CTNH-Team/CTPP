package com.mo_guang.ctpp.client;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

/**
 * Atlas lookups for the goods overlays; all are GT's own art. The still variants use GT's static hatch arrows,
 * since an animated sheet cannot be pinned to frame zero through StaticFaceBakery.
 */
@OnlyIn(Dist.CLIENT)
public final class MarketOverlaySprites {

    private static final ResourceLocation PIPE = GTCEu.id("block/overlay/machine/overlay_pipe");
    private static final ResourceLocation ITEM_OUTPUT_ANIMATED = GTCEu.id("block/overlay/machine/overlay_item_output");
    private static final ResourceLocation ITEM_OUTPUT_STATIC = GTCEu
            .id("block/overlay/machine/overlay_item_hatch_output");
    private static final ResourceLocation FLUID_OUTPUT_ANIMATED = GTCEu
            .id("block/overlay/machine/overlay_fluid_output");
    private static final ResourceLocation FLUID_OUTPUT_STATIC = GTCEu
            .id("block/overlay/machine/overlay_fluid_hatch_output");

    private MarketOverlaySprites() {}

    @Nullable
    private static TextureAtlasSprite sprite(ResourceLocation location) {
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        return atlas == null ? null : atlas.apply(location);
    }

    /** Socket plate for configured faces. */
    @Nullable
    public static TextureAtlasSprite pipe() {
        return sprite(PIPE);
    }

    /** @param proxy animated for a proxy face, still for auto-output */
    @Nullable
    public static TextureAtlasSprite itemOutput(boolean proxy) {
        return sprite(proxy ? ITEM_OUTPUT_ANIMATED : ITEM_OUTPUT_STATIC);
    }

    /** The fluid counterpart, reserved for when fluid proxying is wired up. */
    @Nullable
    public static TextureAtlasSprite fluidOutput(boolean proxy) {
        return sprite(proxy ? FLUID_OUTPUT_ANIMATED : FLUID_OUTPUT_STATIC);
    }
}
