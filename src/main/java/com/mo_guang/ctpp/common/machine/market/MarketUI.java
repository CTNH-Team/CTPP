package com.mo_guang.ctpp.common.machine.market;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.ctnhlang.CN;
import com.ctnhlang.Category;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import com.mo_guang.ctpp.CTPP;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.util.function.Supplier;

/**
 * Lays out the trading machine's screen.
 * <p>
 * The fancy machine shell gives the machine a 176x166 window and always reserves the bottom 86px for the player
 * inventory, so everything here has to fit inside a 176x80 panel.
 * <p>
 * The three functional areas are laid out as columns: tickets on the left with the purse beneath them, the goods
 * on the right with the machine's read-outs below. Slots reuse the stock GT slot texture so the screen sits
 * naturally beside the rest of GT's machines.
 */
@Prefix("market")
@Category("machine")
final class MarketUI {

    @CN("余额：%s")
    @EN("Balance: %s")
    private static Lang gui_balance;

    /** One slot plus its border, matching {@link GuiTextures#SLOT}. */
    private static final int STEP = 18;

    /*
     * Budget: the fancy shell hands the machine a 176x166 window and keeps the bottom 86px for the player
     * inventory, so usable content is 176x80.
     *
     * Three non-overlapping regions:
     *
     * x: 4 76 80 152 154 172
     * +-----------+ +---------------------+ +------+
     * y: 4 | tickets | | goods 4x3 | | tank |
     * | 4x2 | | | | |
     * 40 +-----------+ +---------------------+ +------+
     * | purse 4x2 | | balance / status row over the full width |
     * 76 +-----------+ +------------------------------------------+
     *
     * The tank columns are 4 slots wide on purpose: a 4x4 goods grid plus a tank plus the read-outs does not fit
     * in 176px, so goods carry 12 slots and the read-outs get a full-width strip underneath instead.
     */
    private static final int TICKET_X = 4;
    private static final int TICKET_Y = 4;
    private static final int MONEY_X = 4;
    private static final int MONEY_Y = 40;
    private static final int GOODS_X = 80;
    private static final int GOODS_Y = 4;
    private static final int GOODS_COLUMNS = 4;
    private static final int TANK_X = 154;

    private static final int READOUT_X = 80;
    private static final int READOUT_Y = 62;
    private static final int READOUT_W = 90;

    /** Tickets and the purse are four wide. */
    private static final int COLUMNS = 4;

    private MarketUI() {}

    /**
     * Low-contrast watermarks so the three areas are distinguishable at a glance. Each one is an existing texture
     * pushed through a desaturate-and-darken pass, so they match the item that belongs in the slot: a coin for the
     * purse, an ingot for the goods, and paper for the tickets.
     */
    private static final ResourceTexture WATERMARK_TICKET = watermark("slot_ticket");
    private static final ResourceTexture WATERMARK_GOODS = watermark("slot_goods");
    private static final ResourceTexture WATERMARK_COIN = watermark("slot_coin");

    private static ResourceTexture watermark(String name) {
        return new ResourceTexture(CTPP.id("textures/gui/" + name + ".png"));
    }

    static void build(WidgetGroup group, TradingMachine machine) {
        addSlotGrid(group, machine.ticketInventory.storage, TICKET_X, TICKET_Y, COLUMNS, WATERMARK_TICKET);
        addSlotGrid(group, machine.moneyInventory.storage, MONEY_X, MONEY_Y, COLUMNS, WATERMARK_COIN);
        addSlotGrid(group, machine.goodsInventory.storage, GOODS_X, GOODS_Y, GOODS_COLUMNS, WATERMARK_GOODS);

        // The fluid half of the goods area stands beside the item grid, two slots tall.
        group.addWidget(new TankWidget(machine.goodsTank.getStorages()[0],
                TANK_X, GOODS_Y, STEP, STEP * 2, true, true)
                .setBackground(GuiTextures.FLUID_SLOT));

        group.addWidget(text(READOUT_X, READOUT_Y, READOUT_W, 0xFFC66D,
                () -> gui_balance.translate(CurrencyUtil.format(machine.getMoneyBalance()))));
        // The last trade result is surfaced through GT's side tooltip panel instead of a text line here, so a
        // failure reads the same way it does on any other GT machine.
    }

    /** Dynamic text; the supplier is re-evaluated every frame so synced state shows up live. */
    private static TextTextureWidget text(int x, int y, int width, int colour, Supplier<Component> supplier) {
        TextTextureWidget widget = new TextTextureWidget(x, y, width, 10);
        widget.setText(supplier);
        widget.textureStyle(texture -> texture.setColor(colour));
        return widget;
    }

    private static void addSlotGrid(WidgetGroup group, IItemHandlerModifiable handler, int x, int y, int columns,
                                    ResourceTexture watermark) {
        for (int index = 0; index < handler.getSlots(); index++) {
            int column = index % columns;
            int row = index / columns;
            SlotWidget slot = new SlotWidget(handler, index, x + column * STEP, y + row * STEP, true, true);
            slot.setBackground(slotBackground(watermark));
            group.addWidget(slot);
        }
    }

    /**
     * Slot frame, its darkened inner panel, and the area watermark on top of both.
     * <p>
     * The watermark is drawn last so it sits under the item but over the recess, and it is deliberately faint —
     * it has to identify the area without competing with whatever the player has actually put in the slot.
     */
    private static IGuiTexture slotBackground(ResourceTexture watermark) {
        return new GuiTextureGroup(GuiTextures.SLOT, GuiTextures.SLOT_DARKENED, watermark);
    }
}
