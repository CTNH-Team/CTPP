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
 * Atlas lookups for the trading machine's goods overlays.
 * <p>
 * All three sheets are GT's own art, so they are already stitched and need no registration.
 * <p>
 * {@code overlay_item_output} is a 16x96 animated sheet. Minecraft stitches an animated texture as a single
 * frame-sized tile and cycles the frames into it, so a quad baked against that sprite shows one frame at a time
 * rather than the whole strip. There is no supported way to pin it to frame zero through
 * {@code StaticFaceBakery}, so the still variant uses GT's dedicated static arrow
 * ({@code overlay_item_hatch_output}) instead — same visual language, genuinely non-animated.
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

    /** The socket plate every configured face gets, matching a GT machine's io overlay. */
    @Nullable
    public static TextureAtlasSprite pipe() {
        return sprite(PIPE);
    }

    /**
     * @param proxy {@code true} for the flowing animation of a proxy face, {@code false} for the still arrow of a
     *              plain auto-output face
     */
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
