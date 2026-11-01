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
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
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

import com.mo_guang.ctpp.common.item.MarketTicketItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/** Buys and sells according to the market tickets loaded into it. */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TradingMachine extends TieredEnergyMachine implements IFancyUIMachine, IInteractedMachine {

    public static final int TICKET_SLOTS = 9;
    public static final int GOODS_SLOTS = 27;
    public static final int MONEY_SLOTS = 8;
    public static final int GOODS_TANK_CAPACITY = 16_000;

    /** Base trading rate at LV, before the tier or shaft speed multiplies it. */
    protected static final double TRADES_PER_TICK = 0.05;
    /** Ceiling on banked trades, so a long idle stretch cannot be cashed in as a burst. */
    private static final double MAX_TRADE_DEBT = 16.0;

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

    /** Item channel face and mode; see {@link GoodsIoMode}. Left-click in the directional setup. */
    @Persisted
    @DescSynced
    @RequireRerender
    private @Nullable Direction itemFace;
    /** Fluid channel face and mode; see {@link GoodsIoMode}. Right-click in the directional setup. */
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

    /**
     * Money the machine holds as a number, meaning the part its coin slots do <em>not</em> currently show.
     * <p>
     * This and the purse are disjoint halves of one balance, so {@link #getMoneyBalance()} is their sum. Keeping
     * them separate is what lets a machine pay amounts smaller than its smallest coin, and what makes a purse of
     * eight slots able to hold more than eight slots' worth.
     */
    @Persisted
    @DescSynced
    private long storedBalance;

    @Persisted
    @DescSynced
    private TradeExecutor.Result lastResult = TradeExecutor.Result.NO_ENTRIES;
    /** Sub-trade remainder carried between ticks, so a slow machine still makes steady progress. */
    @Persisted
    private double tradeDebt;

    /** Server-side only: ticket slot that settled last, or -1. */
    private int lastTicketSlot = -1;

    /** EU drawn per settled trade, scaled by tier. */
    private static final long EU_PER_TRADE = 32L;

    public TradingMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier);
        this.ticketInventory = attachTrait(new NotifiableItemStackHandler(this, TICKET_SLOTS, IO.NONE));
        this.ticketInventory.setFilter(MarketTicket::isTicket);
        this.goodsInventory = attachTrait(new NotifiableItemStackHandler(this, GOODS_SLOTS, IO.BOTH));
        // By item type, not MarketTicket::isTicket: that only matches a ticket carrying a receipt.
        this.goodsInventory.setFilter(stack -> !(stack.getItem() instanceof MarketTicketItem));
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
        repairInventorySizes();
        if (!isRemote()) {
            subscribeServerTick(this::tickTrades);
        }
    }

    /** Restores declared slot counts; ItemStackHandler#deserializeNBT reloads the saved Size. */
    private void repairInventorySizes() {
        resizeHandler(ticketInventory.storage, TICKET_SLOTS);
        resizeHandler(goodsInventory.storage, GOODS_SLOTS);
        resizeHandler(moneyInventory.storage, MONEY_SLOTS);
    }

    private static void resizeHandler(CustomItemStackHandler handler, int size) {
        if (handler.getSlots() != size) {
            handler.setSize(size);
        }
    }

    /**
     * Cashes the purse into the balance, trades, and redraws the purse.
     * <p>
     * Folding the purse in first is what lets a deposit be spent the tick it arrives and a purse of large coins
     * make change; redrawing last means the coins on screen are always exactly what is left. Trading in between
     * only ever touches the number.
     */
    private void tickTrades() {
        storedBalance = CurrencyUtil.total(moneyInventory.storage, storedBalance);
        tickAutoOutput();
        try {
            advanceTrading();
        } finally {
            // Unconditional: the purse was folded into the balance above, so skipping this on any early exit would
            // strand coins in the balance and make them vanish from the slots.
            storedBalance = CurrencyUtil.project(moneyInventory.storage, storedBalance);
        }
    }

    /** Charges up and settles trades, or reports why it cannot. Leaves the balance to {@link #tickTrades()}. */
    private void advanceTrading() {
        // Progress only accrues against a trade that could actually settle. Charging ahead whenever a ticket is
        // merely present would let the bar fill against a shop with no stock or no money, then snap back empty and
        // start over — the bar would spin without anything happening.
        if (!canTrade()) {
            tradeDebt = 0.0;
            lastResult = powerFailure();
            return;
        }
        TradeExecutor.Result ready = readiness();
        if (ready != TradeExecutor.Result.SUCCESS) {
            tradeDebt = 0.0;
            lastResult = ready;
            return;
        }
        // Carries the sub-trade remainder between ticks so a slow machine still makes steady progress instead of
        // stalling at zero; a fast one simply banks more than it can spend in one tick.
        tradeDebt = Math.min(tradeDebt + tradesPerTick(), MAX_TRADE_DEBT);
        while (tradeDebt >= 1.0) {
            tradeDebt -= 1.0;
            if (performTrade() != TradeExecutor.Result.SUCCESS) {
                tradeDebt = 0.0;
                break;
            }
        }
    }

    /** How far the machine is toward its next trade, 0 to 1, for the GUI progress bar. */
    public double getTradeProgress() {
        return Math.min(1.0, Math.max(0.0, tradeDebt));
    }

    /**
     * How many trades this machine settles per tick.
     * <p>
     * Electric machines scale with voltage tier and kinetic ones with shaft speed, so upgrading the power supply
     * is what makes a shop faster — there is no fixed cadence to outgrow.
     */
    protected double tradesPerTick() {
        return TRADES_PER_TICK * (1 << Math.max(0, tier - GTValues.LV));
    }

    /** Runs one trade attempt across every ticket slot, stopping at the first success. */
    public TradeExecutor.Result performTrade() {
        lastTicketSlot = -1;
        if (!canTrade()) {
            lastResult = powerFailure();
            return lastResult;
        }
        TradeExecutor.Result overall = TradeExecutor.Result.NO_ENTRIES;
        for (int slot = 0; slot < ticketInventory.getSlots(); slot++) {
            ItemStack ticket = ticketInventory.getStackInSlot(slot);
            if (!MarketTicket.isTicket(ticket)) {
                continue;
            }
            // Trade a copy, so a failed attempt leaves the stored ticket untouched.
            ItemStack working = ticket.copy();
            TradeExecutor.Attempt attempt = TradeExecutor.tryTrade(working, getGoodsAccess(), storedBalance,
                    getPriceResolver(), getLevel());
            if (attempt.isSuccess()) {
                // Quota lives in NBT; write the mutated copy back.
                ticketInventory.setStackInSlot(slot, working);
                storedBalance = attempt.storedBalance();
                lastTicketSlot = slot;
                lastResult = attempt.result();
                afterTrade();
                return lastResult;
            }
            overall = attempt.result();
        }
        lastResult = overall;
        return overall;
    }

    /** Whether the machine has power for another trade. */
    protected boolean canTrade() {
        return energyContainer.getEnergyStored() >= tradeCost();
    }

    /**
     * Whether any ticket slot holds a line that could settle right now, and if not, the reason to report.
     * <p>
     * This is a dry run of the same search {@link #performTrade()} will do, so the answer cannot disagree with what
     * a real attempt would find. It is what keeps the progress bar from filling against a shop that has no stock,
     * no money, or no receipt worth trading.
     *
     * @return {@link TradeExecutor.Result#SUCCESS} when a trade is ready, otherwise why none is
     */
    protected TradeExecutor.Result readiness() {
        TradeExecutor.Result blocker = null;
        for (int slot = 0; slot < ticketInventory.getSlots(); slot++) {
            ItemStack ticket = ticketInventory.getStackInSlot(slot);
            if (!MarketTicket.isTicket(ticket)) {
                continue;
            }
            TradeExecutor.Result result = TradeExecutor.probe(ticket, getGoodsAccess(), storedBalance,
                    getPriceResolver(), getLevel());
            if (result == TradeExecutor.Result.SUCCESS) {
                return result;
            }
            // "Nothing to do here" is the fallback reason; anything else is a concrete blocker worth reporting.
            if (blocker == null && result != TradeExecutor.Result.EXHAUSTED &&
                    result != TradeExecutor.Result.NO_ENTRIES) {
                blocker = result;
            }
        }
        return blocker != null ? blocker : TradeExecutor.Result.EXHAUSTED;
    }

    /** Result reported when {@link #canTrade()} is false. */
    protected TradeExecutor.Result powerFailure() {
        return TradeExecutor.Result.NO_EU;
    }

    /** Total money held: the stored balance plus the coins on display in the purse. */
    public long getMoneyBalance() {
        return CurrencyUtil.total(moneyInventory.storage, storedBalance);
    }

    /** The part of the balance not currently shown as coins. */
    public long getStoredBalance() {
        return storedBalance;
    }

    /**
     * How prices are decided. Fixed by default; a supply-and-demand model would replace this without touching the
     * trade logic.
     */
    protected PriceResolver getPriceResolver() {
        return PriceResolver.FIXED;
    }

    /** EU for one trade. */
    protected long tradeCost() {
        return EU_PER_TRADE * (1L << tier);
    }

    /** Charges the machine for one settled trade. */
    protected void afterTrade() {
        energyContainer.removeEnergy(tradeCost());
    }

    //////////////////////////////////////
    // ***** Inventory wiring *****//
    //////////////////////////////////////

    protected IItemHandlerModifiable getMoneyHandler() {
        return moneyInventory.storage;
    }

    /** The face the trade proxies through, or null. */
    @Nullable
    public Direction getProxySide() {
        if (itemMode == GoodsIoMode.PROXY && itemFace != null) {
            return itemFace;
        }
        return null;
    }

    /** The inventory trades read from and write to: the proxied neighbour, else the internal goods area. */
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

    /** Selects {@code face} for items, or steps its mode if already selected. */
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

    /** The mode currently shown for a face. */
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

    //////////////////////////////////////
    // ***** Auto output *****//
    //////////////////////////////////////

    /** Pushes stock out of the {@link GoodsIoMode#AUTO_OUTPUT} faces. Proxy faces are skipped. */
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
        // Face modes are set in the directional setup screen.
        return InteractionResult.PASS;
    }

    //////////////////////////////////////
    // ***** GUI *****//
    //////////////////////////////////////

    @Override
    public Widget createUIWidget() {
        // No background: this group spans the player-inventory strip too.
        WidgetGroup group = new WidgetGroup(0, 0, MarketUI.PAGE_WIDTH, MarketUI.PAGE_HEIGHT);
        MarketUI.build(group, this);
        return group;
    }

    /** Adds the info line and, on failure, GT's fault indicator to the side tooltip panel. */
    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        tooltipsPanel.attachTooltips(new IFancyTooltip.Basic(
                () -> GuiTextures.INFO_ICON,
                this::energyTooltip,
                () -> true,
                () -> null));

        // Fault indicator.
        tooltipsPanel.attachTooltips(new IFancyTooltip.Basic(
                () -> GuiTextures.INSUFFICIENT_INPUT,
                () -> List.of(lastResult.describe().withStyle(ChatFormatting.RED)),
                () -> lastResult != TradeExecutor.Result.SUCCESS && lastResult != TradeExecutor.Result.NO_ENTRIES &&
                        lastResult != TradeExecutor.Result.EXHAUSTED,
                () -> null));
    }

    /** Lines shown on the info icon. */
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
        return false;
    }
}
