package com.mo_guang.ctpp.common.machine.market;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.widget.directional.IDirectionalConfigHandler;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.ctnhlang.CN;
import com.ctnhlang.Category;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import com.mojang.blaze3d.vertex.PoseStack;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * The goods rows of the trading machine's directional setup screen.
 * <p>
 * This is a deliberate copy of GT's {@code AutoOutputItemConfigHandler} / {@code AutoOutputFluidConfigHandler}
 * pair, so the screen behaves exactly like a stock GT machine: two buttons, the left one for items and the right
 * one for fluids, and clicking a face in the preview pushes that face through the same cycle GT uses.
 * <p>
 * The difference is the third mode. GT cycles off → output → auto; here "auto" is replaced by {@code PROXY}, which
 * makes the trade itself read and write the neighbour's inventory instead of the machine's own stock. The button
 * art is GT's own three-way io sprite sheet, so the control reads identically.
 */
@Prefix("market")
@Category("machine")
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MarketIoConfigHandler implements IDirectionalConfigHandler {

    private static final IGuiTexture TEXTURE_OFF = modeButton(GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON, 0);
    private static final IGuiTexture TEXTURE_OUTPUT = modeButton(GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON, 1);
    private static final IGuiTexture TEXTURE_PROXY = modeButton(GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON, 2);

    private static final IGuiTexture TEXTURE_OFF_FLUID = modeButton(GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON, 0);
    private static final IGuiTexture TEXTURE_OUTPUT_FLUID = modeButton(GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON, 1);
    private static final IGuiTexture TEXTURE_PROXY_FLUID = modeButton(GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON, 2);

    private static IGuiTexture modeButton(ResourceTexture sheet, int index) {
        return new GuiTextureGroup(GuiTextures.VANILLA_BUTTON, sheet.getSubTexture(0, index / 3f, 1, 1 / 3f));
    }

    @CN("左键选择物品面，右键选择流体面")
    @EN("Left-click picks the item face, right-click the fluid face")
    private static Lang goods_unselected;

    @CN("物品：%s（再次点击切换模式）")
    @EN("Items: %s (click again to cycle)")
    private static Lang goods_item_selected;

    @CN("流体：%s（再次点击切换模式）")
    @EN("Fluids: %s (click again to cycle)")
    private static Lang goods_fluid_selected;

    @CN("无")
    @EN("None")
    private static Lang mode_none;

    @CN("自动输出")
    @EN("Auto Output")
    private static Lang mode_auto_output;

    @CN("代理")
    @EN("Proxy")
    private static Lang mode_proxy;

    private final TradingMachine machine;
    private Direction side;
    private ButtonWidget itemButton;
    private ButtonWidget fluidButton;

    public MarketIoConfigHandler(TradingMachine machine) {
        this.machine = machine;
    }

    private static IGuiTexture textureFor(boolean fluid, GoodsIoMode mode) {
        return switch (mode) {
            case NONE -> fluid ? TEXTURE_OFF_FLUID : TEXTURE_OFF;
            case AUTO_OUTPUT -> fluid ? TEXTURE_OUTPUT_FLUID : TEXTURE_OUTPUT;
            case PROXY -> fluid ? TEXTURE_PROXY_FLUID : TEXTURE_PROXY;
        };
    }

    private static Component modeName(GoodsIoMode mode) {
        return switch (mode) {
            case NONE -> mode_none.translate();
            case AUTO_OUTPUT -> mode_auto_output.translate();
            case PROXY -> mode_proxy.translate();
        };
    }

    @Override
    public Widget getSideSelectorWidget(SceneWidget scene, FancyMachineUIWidget machineUI) {
        // Same shape as GT: two 18x18 buttons side by side, items first.
        WidgetGroup group = new WidgetGroup(0, 0, (18 * 2) + 1, 18);

        itemButton = new ButtonWidget(0, 0, 18, 18, this::onItemPressed) {

            @Override
            public void updateScreen() {
                super.updateScreen();
                GoodsIoMode mode = side != null && side.equals(machine.getItemFace()) ? machine.getItemMode() :
                        GoodsIoMode.NONE;
                setButtonTexture(textureFor(false, mode));
                setHoverTooltips(
                        side == null ? goods_unselected.translate() : goods_item_selected.translate(modeName(mode)));
            }
        };

        fluidButton = new ButtonWidget(19, 0, 18, 18, this::onFluidPressed) {

            @Override
            public void updateScreen() {
                super.updateScreen();
                GoodsIoMode mode = side != null && side.equals(machine.getFluidFace()) ? machine.getFluidMode() :
                        GoodsIoMode.NONE;
                setButtonTexture(textureFor(true, mode));
                setHoverTooltips(
                        side == null ? goods_unselected.translate() : goods_fluid_selected.translate(modeName(mode)));
            }
        };

        group.addWidget(itemButton);
        group.addWidget(fluidButton);
        return group;
    }

    private void onItemPressed(ClickData clickData) {
        if (side != null) {
            machine.selectItemFace(side);
        }
    }

    private void onFluidPressed(ClickData clickData) {
        if (side != null) {
            machine.selectFluidFace(side);
        }
    }

    @Override
    public void onSideSelected(BlockPos pos, Direction side) {
        this.side = side;
    }

    @Override
    public ScreenSide getScreenSide() {
        return ScreenSide.LEFT;
    }

    /**
     * Clicking a face in the 3D preview applies the channel matching the mouse button, exactly like GT: left for
     * items, right for fluids.
     */
    @Override
    public void handleClick(ClickData clickData, Direction direction) {
        if (side == null || !side.equals(direction)) {
            return;
        }
        if (clickData.button == 0) {
            machine.selectItemFace(direction);
        } else if (clickData.button == 1) {
            machine.selectFluidFace(direction);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderOverlay(SceneWidget sceneWidget, BlockPosFace blockPosFace) {
        // Item faces read orange and fluid faces blue, matching GT's own auto-output highlight colours.
        if (blockPosFace.facing.equals(machine.getItemFace()) && machine.getItemMode() != GoodsIoMode.NONE) {
            sceneWidget.drawFacingBorder(new PoseStack(), blockPosFace,
                    machine.getItemMode() == GoodsIoMode.PROXY ? 0xff20b2ff : 0xffff6e0f, 1);
        }
        if (blockPosFace.facing.equals(machine.getFluidFace()) && machine.getFluidMode() != GoodsIoMode.NONE) {
            sceneWidget.drawFacingBorder(new PoseStack(), blockPosFace,
                    machine.getFluidMode() == GoodsIoMode.PROXY ? 0xff00b4ff : 0x8f00b4ff, 2);
        }
    }
}
