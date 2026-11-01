package com.mo_guang.ctpp.client;

import com.gregtechceu.gtceu.api.gui.widget.TankWidget;

import com.lowdragmc.lowdraglib.gui.util.TextFormattingUtil;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;

/**
 * Tank widget with the amount in the bottom-right corner; {@link TankWidget} hard-codes a centred position with no
 * setter. Fill ratio, hover and tooltip are inherited.
 */
@OnlyIn(Dist.CLIENT)
public class MarketTankWidget extends TankWidget {

    private static final int TEXT_COLOUR = 0xFFFFFF;
    /** Inset from the widget edge, in the half-scale space the text is drawn in. */
    private static final int INSET = 2;

    public MarketTankWidget(IFluidHandler tank, int x, int y, int width, int height,
                            boolean allowClickFilled, boolean allowClickDrained) {
        super(tank, x, y, width, height, allowClickFilled, allowClickDrained);
        // Protected with no setter; this class draws its own overlay instead.
        this.showAmountOverlay = false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);

        FluidStack fluid = getFluid();
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        String amount = TextFormattingUtil.formatLongToCompactStringBuckets(fluid.getAmount(), 3) + "B";

        Position pos = getPosition();
        Size size = getSize();
        Font font = Minecraft.getInstance().font;

        // Drawn at half scale, so the anchor is computed in the doubled coordinate space.
        graphics.pose().pushPose();
        graphics.pose().scale(0.5F, 0.5F, 1);
        int right = (pos.x + size.width - INSET) * 2;
        int bottom = (pos.y + size.height - INSET) * 2;
        graphics.drawString(font, amount, right - font.width(amount), bottom - font.lineHeight, TEXT_COLOUR, true);
        graphics.pose().popPose();
    }
}
