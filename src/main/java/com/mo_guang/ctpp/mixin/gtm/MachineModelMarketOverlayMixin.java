package com.mo_guang.ctpp.mixin.gtm;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.model.GTModelProperties;
import com.gregtechceu.gtceu.client.model.machine.MachineModel;
import com.gregtechceu.gtceu.client.util.StaticFaceBakery;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import com.mo_guang.ctpp.client.MarketOverlaySprites;
import com.mo_guang.ctpp.common.machine.market.GoodsIoMode;
import com.mo_guang.ctpp.common.machine.market.TradingMachine;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Paints the goods-area overlays onto trading machines.
 * <p>
 * GT's {@link MachineModel} only knows about {@code AutoOutputTrait}, so a face set to auto-output or proxy would
 * otherwise be invisible. This mirrors that logic for the market's own per-face modes:
 * <ul>
 * <li>every configured face gets {@code overlay_pipe} as the socket plate, matching a GT machine's io overlay;</li>
 * <li>auto-output adds the first frame of {@code overlay_item_output} (the still arrow);</li>
 * <li>proxy adds the full animated {@code overlay_item_output} sheet, so the face reads as "feeding through".</li>
 * </ul>
 */
@Mixin(value = MachineModel.class, remap = false)
public abstract class MachineModelMarketOverlayMixin {

    @Inject(method = "getMachineQuads", at = @At("RETURN"), remap = false)
    private void ctpp$addMarketOverlays(@Nullable BlockState blockState, @Nullable Direction side,
                                        RandomSource rand, ModelData modelData, @Nullable RenderType renderType,
                                        CallbackInfoReturnable<List<BakedQuad>> cir) {
        if (side == null) {
            return;
        }
        BlockAndTintGetter level = modelData.get(GTModelProperties.LEVEL);
        BlockPos pos = modelData.get(GTModelProperties.POS);
        if (level == null || pos == null) {
            return;
        }
        if (!(MetaMachine.getMachine(level, pos) instanceof TradingMachine machine)) {
            return;
        }
        GoodsIoMode itemMode = side.equals(machine.getItemFace()) ? machine.getItemMode() : GoodsIoMode.NONE;
        GoodsIoMode fluidMode = side.equals(machine.getFluidFace()) ? machine.getFluidMode() : GoodsIoMode.NONE;
        if (itemMode == GoodsIoMode.NONE && fluidMode == GoodsIoMode.NONE) {
            return;
        }
        List<BakedQuad> quads = cir.getReturnValue();
        TextureAtlasSprite pipe = MarketOverlaySprites.pipe();
        if (pipe == null) {
            return;
        }
        quads.add(StaticFaceBakery.bakeFace(StaticFaceBakery.OUTPUT_OVERLAY, side, pipe));
        if (itemMode != GoodsIoMode.NONE) {
            addIcon(quads, side, MarketOverlaySprites.itemOutput(itemMode == GoodsIoMode.PROXY));
        }
        if (fluidMode != GoodsIoMode.NONE) {
            addIcon(quads, side, MarketOverlaySprites.fluidOutput(fluidMode == GoodsIoMode.PROXY));
        }
    }

    private static void addIcon(List<BakedQuad> quads, Direction side, @Nullable TextureAtlasSprite icon) {
        if (icon != null) {
            quads.add(StaticFaceBakery.bakeFace(StaticFaceBakery.AUTO_OUTPUT_OVERLAY, side, icon));
        }
    }
}
