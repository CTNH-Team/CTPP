package com.mo_guang.ctpp.common.machine.market;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.ctnhlang.CN;
import com.ctnhlang.Category;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import com.mo_guang.ctpp.CTPP;
import com.mo_guang.ctpp.client.MarketSlotWidget;
import com.mo_guang.ctpp.client.MarketTankWidget;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Lays out the trading machine's screen.
 * <p>
 * The fancy machine shell gives the machine a 176x166 window and always reserves the bottom 86px for the player
 * inventory, so everything here has to fit inside a 176x80 panel.
 * <p>
 * The goods grid sits on top with the fluid tank beside it; the bottom row is the purse and its balance read-out on
 * the left, GT's progress arrow in the middle, and the receipts on the right. Slots reuse the stock GT slot texture
 * so the screen sits naturally beside the rest of GT's machines.
 */
@Prefix("market")
@Category("machine")
final class MarketUI {

    @CN("余额：%s")
    @EN("Balance: %s")
    private static Lang gui_balance;

    /** One slot plus its border, matching {@link GuiTextures#SLOT}. */
    private static final int STEP = 18;

    // Larger than the default 176x166 machine window, so the player inventory is pushed below the content.
    static final int PAGE_WIDTH = 190;
    static final int PAGE_HEIGHT = 112;

    private static final int GOODS_X = 4;
    private static final int GOODS_Y = 4;
    private static final int GOODS_COLUMNS = 9;

    /** Nine slots need a 3x3 block. */
    private static final int TICKET_X = 96;
    private static final int TICKET_Y = 58;
    private static final int TICKET_COLUMNS = 3;

    /** Eight slots are laid out 4x2 rather than 2x4 so the balance line still fits underneath. */
    private static final int MONEY_X = 4;
    private static final int MONEY_Y = 58;
    private static final int MONEY_COLUMNS = 4;

    /** Directly under the purse. */
    private static final int READOUT_X = 4;
    private static final int READOUT_Y = 96;
    private static final int READOUT_W = 88;

    /** GT's arrow sheet is 20x40 with two 20x20 frames; drawing it at any other size stretches it. */
    private static final int PROGRESS_W = 20;
    private static final int PROGRESS_H = 20;
    /** Exactly fills the 20px the purse (ends x 76) and the receipts (start x 96) leave between them. */
    private static final int PROGRESS_X = 76;
    private static final int PROGRESS_Y = 66;

    private static final int TANK_X = 168;
    private static final int TANK_Y = 4;

    private MarketUI() {}

    /** Faint slot watermarks identifying each area. */
    private static final ResourceTexture WATERMARK_TICKET = watermark("slot_ticket");
    private static final ResourceTexture WATERMARK_GOODS = watermark("slot_goods");
    private static final ResourceTexture WATERMARK_COIN = watermark("slot_coin");

    private static ResourceTexture watermark(String name) {
        return new ResourceTexture(CTPP.id("textures/gui/" + name + ".png"));
    }

    static void build(WidgetGroup group, TradingMachine machine) {
        // Each grid claims its own kind so shift-click lands in the right region.
        addSlotGrid(group, machine.goodsInventory.storage, GOODS_X, GOODS_Y, GOODS_COLUMNS, WATERMARK_GOODS,
                MarketUI::isGoods);
        addSlotGrid(group, machine.ticketInventory.storage, TICKET_X, TICKET_Y, TICKET_COLUMNS, WATERMARK_TICKET,
                MarketTicket::isTicket);
        addSlotGrid(group, machine.moneyInventory.storage, MONEY_X, MONEY_Y, MONEY_COLUMNS, WATERMARK_COIN,
                CoinDenominationRegistry::isCoin);

        // setFillDirection is required: the default ALWAYS_FULL ignores the fill ratio.
        group.addWidget(new MarketTankWidget(machine.goodsTank.getStorages()[0],
                TANK_X, TANK_Y, STEP, STEP * 2, true, true)
                .setBackground(GuiTextures.FLUID_SLOT)
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP));

        // GT's arrow progress bar sits between the purse and the receipts. The ResourceTexture constructor splits
        // the 20x40 sheet into its dim and lit frames; drawing the sheet directly would squash both into one
        // widget. The arrow painted in the texture points right, so it has to fill left-to-right: revealing it from
        // the other side would show the head before the shaft and read as running backwards.
        group.addWidget(new ProgressWidget(machine::getTradeProgress,
                PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, GuiTextures.PROGRESS_BAR_ARROW)
                .setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT));

        group.addWidget(text(READOUT_X, READOUT_Y, READOUT_W, 0xFFC66D,
                () -> gui_balance.translate(CurrencyUtil.format(machine.getMoneyBalance()))));
        // The last trade result is surfaced through GT's side tooltip panel instead of a text line here, so a
        // failure reads the same way it does on any other GT machine.
    }

    /** Text re-evaluated every frame. */
    private static TextTextureWidget text(int x, int y, int width, int colour, Supplier<Component> supplier) {
        TextTextureWidget widget = new TextTextureWidget(x, y, width, 10);
        widget.setText(supplier);
        widget.textureStyle(texture -> texture.setColor(colour));
        return widget;
    }

    /** Goods are anything that is neither a ticket nor a coin; those two have their own regions. */
    private static boolean isGoods(ItemStack stack) {
        return !MarketTicket.isTicket(stack) && !CoinDenominationRegistry.isCoin(stack);
    }

    /** @param shiftClickFilter what this region accepts from a shift-click; LowDragLib sorts destinations by index. */
    private static void addSlotGrid(WidgetGroup group, IItemHandlerModifiable handler, int x, int y, int columns,
                                    ResourceTexture watermark, Predicate<ItemStack> shiftClickFilter) {
        for (int index = 0; index < handler.getSlots(); index++) {
            int column = index % columns;
            int row = index / columns;
            SlotWidget slot = new MarketSlotWidget(handler, index, x + column * STEP, y + row * STEP,
                    shiftClickFilter);
            slot.setBackground(slotBackground(watermark));
            group.addWidget(slot);
        }
    }

    /** Slot frame plus watermark; anything opaque over it would flatten the bevel. */
    private static IGuiTexture slotBackground(ResourceTexture watermark) {
        return new GuiTextureGroup(GuiTextures.SLOT, watermark);
    }
}
