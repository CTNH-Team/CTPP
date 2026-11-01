package com.mo_guang.ctpp.common.machine.market;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * The CTPP trading machine: one block that both buys and sells, driven entirely by market tickets.
 * <p>
 * The machine's state is split into the three areas a shop needs:
 * <ul>
 * <li><b>goods</b> — {@link #goodsInventory} and {@link #goodsTank}, holding what the machine can sell and
 * collecting what it buys;</li>
 * <li><b>tickets</b> — {@link #ticketInventory}, scanned in order until a line settles;</li>
 * <li><b>money</b> — the {@link #moneyInventory} purse, which needs room before the machine takes a payment.</li>
 * </ul>
 * One trade settles per {@link #TRADE_INTERVAL} ticks. With {@link #externalGoods} enabled the machine trades
 * against the inventory in front of it instead of its own stock, which is how a wall of shops is fed from a
 * central warehouse.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TradingMachine extends TieredEnergyMachine implements IFancyUIMachine, IInteractedMachine {

    /** Ticket slots, scanned top to bottom. */
    public static final int TICKET_SLOTS = 8;
    /** Goods slots, shared between outgoing stock and incoming purchases. */
    public static final int GOODS_SLOTS = 12;
    /** Purse slots; eight stacks of coins is plenty of headroom for change. */
    public static final int MONEY_SLOTS = 8;
    /** Fluid buffer used when a ticket names a fluid instead of an item. */
    public static final int GOODS_TANK_CAPACITY = 16_000;

    /** Ticks between trade batches; keeps a busy shop from melting the server. */
    private static final int TRADE_INTERVAL = 20;
    /**
     * Trades settled per batch. One trade is one item or one mB, so a machine working at all moves a stack a minute.
     */
    private static final int TRADES_PER_CYCLE = 16;

    @Persisted
    @DescSynced
    public final NotifiableItemStackHandler ticketInventory;
    @Persisted
    @DescSynced
    public final NotifiableItemStackHandler goodsInventory;
    @Persisted
    @DescSynced
    public final NotifiableItemStackHandler moneyInventory;
    @Persisted
    @DescSynced
    public final NotifiableFluidTank goodsTank;

    /*
     * Goods io follows GT's own auto-output shape: one selected face per channel, each with its own mode.
     *
     * left click on a face -> picks the item channel's face, cycling its mode
     * right click on a face -> picks the fluid channel's face, cycling its mode
     *
     * The mode itself is none -> auto output -> proxy. "Auto output" pushes the machine's own stock out of that
     * face; "proxy" makes the trade read and write the neighbour's inventory instead, so a shop can be fed from a
     * warehouse without the goods ever entering the machine.
     */
    @Persisted
    @DescSynced
    @RequireRerender
    private @Nullable Direction itemFace;
    @Persisted
    @DescSynced
    @RequireRerender
    private @Nullable Direction fluidFace;
    @Persisted
    @DescSynced
    @RequireRerender
    private GoodsIoMode itemMode = GoodsIoMode.NONE;
    @Persisted
    @DescSynced
    @RequireRerender
    private GoodsIoMode fluidMode = GoodsIoMode.NONE;

    @Persisted
    @DescSynced
    private TradeExecutor.Result lastResult = TradeExecutor.Result.NO_ENTRIES;
    @Persisted
    @DescSynced
    private int tradeCooldown;

    /** Server-side only: ticket slot that settled last, or -1. Purely for feedback. */
    private int lastTicketSlot = -1;

    /** EU drawn from the energy buffer for each settled trade, scaled by tier. */
    private static final long EU_PER_TRADE = 32L;

    public TradingMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier);
        this.ticketInventory = attachTrait(new NotifiableItemStackHandler(this, TICKET_SLOTS, IO.NONE));
        this.ticketInventory.setFilter(MarketTicket::isTicket);
        this.goodsInventory = attachTrait(new NotifiableItemStackHandler(this, GOODS_SLOTS, IO.BOTH));
        this.moneyInventory = attachTrait(new NotifiableItemStackHandler(this, MONEY_SLOTS, IO.NONE));
        this.moneyInventory.setFilter(CoinDenominationRegistry::isCoin);
        this.goodsTank = attachTrait(new NotifiableFluidTank(this, 1, GOODS_TANK_CAPACITY, IO.BOTH));
    }

    //////////////////////////////////////
    // ***** Lifecycle *****//
    //////////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            subscribeServerTick(this::tickTrades);
        }
    }

    private void tickTrades() {
        tickAutoOutput();
        if (tradeCooldown > 0) {
            tradeCooldown--;
            return;
        }
        tradeCooldown = TRADE_INTERVAL;
        // One trade is a single item or a single millibucket, so settling only one per second would move a stack
        // in over a minute. Each cycle settles up to TRADES_PER_CYCLE lines instead, which keeps a shop useful
        // without giving it unbounded per-tick work.
        for (int i = 0; i < TRADES_PER_CYCLE; i++) {
            if (performTrade() != TradeExecutor.Result.SUCCESS) {
                break;
            }
        }
    }

    /**
     * Runs one trade attempt across every ticket slot, stopping at the first ticket that settles.
     *
     * @return the outcome of the attempt
     */
    public TradeExecutor.Result performTrade() {
        lastTicketSlot = -1;
        // No power means no trade at all. A full purse is handled per entry inside the executor instead, because
        // it only stops selling: buying takes coins back out of the purse and stays available.
        if (!canTrade()) {
            lastResult = powerFailure();
            return lastResult;
        }
        TradeExecutor.Result overall = TradeExecutor.Result.NO_ENTRIES;
        IItemHandler money = getMoneyHandler();
        for (int slot = 0; slot < ticketInventory.getSlots(); slot++) {
            ItemStack ticket = ticketInventory.getStackInSlot(slot);
            if (!MarketTicket.isTicket(ticket)) {
                continue;
            }
            // Trade against a copy so a rejected attempt cannot disturb the stored ticket.
            ItemStack working = ticket.copy();
            TradeExecutor.Result result = TradeExecutor.tryTrade(working, getGoodsAccess(), money,
                    isMoneyBufferFull());
            if (result == TradeExecutor.Result.SUCCESS) {
                // Quotas live in NBT, so the mutated copy has to go back into the slot.
                ticketInventory.setStackInSlot(slot, working);
                lastTicketSlot = slot;
                lastResult = result;
                afterTrade();
                return result;
            }
            overall = result;
        }
        lastResult = overall;
        return overall;
    }

    /**
     * Whether the machine has power for another trade.
     * <p>
     * The purse is deliberately <em>not</em> checked here. A full money buffer only stops the machine from
     * <em>selling</em> — it can still buy, because buying drains coins back out of the purse. That is passed into
     * the executor as a separate flag so the block applies to the sell direction alone.
     */
    protected boolean canTrade() {
        return energyContainer.getEnergyStored() >= tradeCost();
    }

    /** The failure to report when {@link #canTrade()} says no. Overridden by the kinetic variant. */
    protected TradeExecutor.Result powerFailure() {
        return TradeExecutor.Result.NO_EU;
    }

    /**
     * True when no further coins of any denomination can be added to the purse.
     * <p>
     * Checked by simulating a single coin deposit, which is the cheapest possible addition; if even that will not
     * fit, the buffer is full.
     */
    public boolean isMoneyBufferFull() {
        return CurrencyUtil.deposit(moneyInventory.storage, 1L, true) > 0;
    }

    /** EU for one trade, scaled by tier so a higher-tier shop moves the same goods for less relative cost. */
    protected long tradeCost() {
        return EU_PER_TRADE * (1L << tier);
    }

    /**
     * Charges the machine for one settled trade.
     * <p>
     * The electric variants pull EU out of their buffer; the kinetic variant overrides both this and
     * {@link #canTrade()} to bill the Create network instead.
     */
    protected void afterTrade() {
        energyContainer.removeEnergy(tradeCost());
    }

    //////////////////////////////////////
    // ***** Inventory wiring *****//
    //////////////////////////////////////

    protected IItemHandlerModifiable getMoneyHandler() {
        return moneyInventory.storage;
    }

    /**
     * The face the trade proxies through, or null. Proxying reads and writes the neighbouring inventory instead of
     * the machine's own goods area.
     */
    @Nullable
    public Direction getProxySide() {
        if (itemMode == GoodsIoMode.PROXY && itemFace != null) {
            return itemFace;
        }
        return null;
    }

    /**
     * Resolves the inventory the trade should read from and write to: the proxied neighbour when a face is set to
     * proxy, otherwise the machine's own goods area.
     */
    public IItemHandler getGoodsHandler() {
        Direction proxy = getProxySide();
        if (proxy != null) {
            IItemHandler neighbour = getNeighbourHandler(proxy);
            if (neighbour != null) {
                return neighbour;
            }
        }
        return goodsInventory.storage;
    }

    /** The fluid half of the goods area, proxied through its own face when one is set to proxy. */
    public IFluidHandler getGoodsFluids() {
        if (fluidMode == GoodsIoMode.PROXY && fluidFace != null) {
            IFluidHandler neighbour = getNeighbourFluids(fluidFace);
            if (neighbour != null) {
                return neighbour;
            }
        }
        return goodsTank;
    }

    @Nullable
    protected IItemHandler getNeighbourHandler(Direction side) {
        Level level = getLevel();
        if (level == null) {
            return null;
        }
        BlockEntity neighbour = level.getBlockEntity(getPos().relative(side));
        if (neighbour == null) {
            return null;
        }
        return neighbour.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
    }

    @Nullable
    protected IFluidHandler getNeighbourFluids(Direction side) {
        Level level = getLevel();
        if (level == null) {
            return null;
        }
        BlockEntity neighbour = level.getBlockEntity(getPos().relative(side));
        if (neighbour == null) {
            return null;
        }
        return neighbour.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).orElse(null);
    }

    public GoodsAccess getGoodsAccess() {
        return new GoodsAccess.Handlers(getGoodsHandler(), getGoodsFluids());
    }

    //////////////////////////////////////
    // ***** State *****//
    //////////////////////////////////////

    //////////////////////////////////////
    // ***** Goods io state *****//
    //////////////////////////////////////

    public @Nullable Direction getItemFace() {
        return itemFace;
    }

    public @Nullable Direction getFluidFace() {
        return fluidFace;
    }

    public GoodsIoMode getItemMode() {
        return itemMode;
    }

    public GoodsIoMode getFluidMode() {
        return fluidMode;
    }

    /**
     * Applies a click on {@code face} to the item channel, mirroring GT's auto-output handler: the first click
     * selects the face and steps the mode on, later clicks on the same face just step the mode.
     */
    public void selectItemFace(Direction face) {
        if (itemFace != face) {
            itemFace = face;
            itemMode = GoodsIoMode.AUTO_OUTPUT;
        } else {
            itemMode = itemMode.next();
        }
        markDirty();
        scheduleRenderUpdate();
    }

    /** The fluid counterpart of {@link #selectItemFace(Direction)}. */
    public void selectFluidFace(Direction face) {
        if (fluidFace != face) {
            fluidFace = face;
            fluidMode = GoodsIoMode.AUTO_OUTPUT;
        } else {
            fluidMode = fluidMode.next();
        }
        markDirty();
        scheduleRenderUpdate();
    }

    /** The mode currently shown for a face, for the setup screen's per-face read-out. */
    public GoodsIoMode modeOnFace(Direction face) {
        if (face.equals(itemFace)) {
            return itemMode;
        }
        if (face.equals(fluidFace)) {
            return fluidMode;
        }
        return GoodsIoMode.NONE;
    }

    public TradeExecutor.Result getLastResult() {
        return lastResult;
    }

    public int getLastTicketSlot() {
        return lastTicketSlot;
    }

    public long getMoneyBalance() {
        return CurrencyUtil.getBalance(moneyInventory.storage);
    }

    //////////////////////////////////////
    // ***** Auto output *****//
    //////////////////////////////////////

    /**
     * Pushes the machine's own stock out of whichever faces are set to {@link GoodsIoMode#AUTO_OUTPUT}.
     * <p>
     * Proxy faces are skipped on purpose: there the neighbour <em>is</em> the stock, so moving goods out of it
     * would just shuffle them between two slots of the same warehouse.
     */
    private void tickAutoOutput() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        if (itemFace != null && itemMode == GoodsIoMode.AUTO_OUTPUT) {
            Direction side = itemFace;
            GTTransferUtils.getAdjacentItemHandler(level, getPos(), side)
                    .ifPresent(destination -> GTTransferUtils.transferItemsFiltered(goodsInventory.storage,
                            destination, getItemCapFilter(side, IO.OUT)));
        }
        if (fluidFace != null && fluidMode == GoodsIoMode.AUTO_OUTPUT) {
            Direction side = fluidFace;
            GTTransferUtils.getAdjacentFluidHandler(level, getPos(), side)
                    .ifPresent(destination -> GTTransferUtils.transferFluidsFiltered(goodsTank,
                            destination, getFluidCapFilter(side, IO.OUT)));
        }
    }

    //////////////////////////////////////
    // ***** Interaction *****//
    //////////////////////////////////////

    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        // Face modes live in the directional setup screen, so the block itself stays a plain machine.
        return InteractionResult.PASS;
    }

    //////////////////////////////////////
    // ***** GUI *****//
    //////////////////////////////////////

    @Override
    public Widget createUIWidget() {
        // No background of our own: the fancy shell already paints one, and this group spans the whole window
        // including the player-inventory strip, so repainting it would cover the player's own slots.
        WidgetGroup group = new WidgetGroup(0, 0, 176, 166);
        MarketUI.build(group, this);
        return group;
    }

    /**
     * Surfaces the machine's state through GT's side tooltip panel, using the same icons and wording as a stock
     * GT machine: the red no-energy indicator for a fault, and the informational energy line otherwise.
     */
    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        // Always-on: what this machine draws, in the same wording GT uses on its own tooltips.
        tooltipsPanel.attachTooltips(new IFancyTooltip.Basic(
                () -> GuiTextures.INFO_ICON,
                this::energyTooltip,
                () -> true,
                () -> null));

        // Fault indicator, shown only when the last attempt failed for a reason worth acting on.
        tooltipsPanel.attachTooltips(new IFancyTooltip.Basic(
                () -> GuiTextures.INSUFFICIENT_INPUT,
                () -> List.of(lastResult.describe().withStyle(ChatFormatting.RED)),
                () -> lastResult != TradeExecutor.Result.SUCCESS && lastResult != TradeExecutor.Result.NO_ENTRIES &&
                        lastResult != TradeExecutor.Result.EXHAUSTED,
                () -> null));
    }

    /** The energy/stress summary shown on the info icon, mirroring GT's own tooltip wording. */
    protected List<Component> energyTooltip() {
        return List.of(
                Component.translatable("gtceu.universal.tooltip.voltage_in",
                        FormattingUtil.formatNumbers(GTValues.V[tier]), GTValues.VNF[tier]),
                Component.translatable("gtceu.universal.tooltip.energy_storage_capacity",
                        FormattingUtil.formatNumbers(energyContainer.getEnergyCapacity())),
                Component.translatable("gtceu.top.energy_consumption",
                        FormattingUtil.formatNumbers(tradeCost())));
    }

    @Override
    public boolean shouldWeatherOrTerrainExplosion() {
        // A shop counter is not a boiler: keep GT's environmental damage rules off it.
        return false;
    }
}
