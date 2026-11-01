package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.ctnhlang.CN;
import com.ctnhlang.Category;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.util.List;

/**
 * Settles one line of one ticket against the machine's goods and money. Stateless.
 * <p>
 * Money is handled purely as a number here: the caller absorbs its purse into a balance before calling and projects
 * that balance back into coins afterwards. Trading therefore never reads or writes coin slots, which is what keeps
 * a settlement from losing money it has already paid out.
 */
@Prefix("market")
@Category("machine")
public final class TradeExecutor {

    private TradeExecutor() {}

    @CN("交易成功")
    @EN("Trade settled")
    private static Lang status_success;

    @CN("没有可读取的单据条目")
    @EN("No readable ticket entries")
    private static Lang status_no_entries;

    @CN("单据额度已用尽")
    @EN("All ticket quotas are spent")
    private static Lang status_exhausted;

    @CN("资金不足")
    @EN("Not enough money")
    private static Lang status_machine_cannot_pay;

    @CN("货物不足")
    @EN("Out of stock")
    private static Lang status_machine_out_of_stock;

    @CN("货物空间不足")
    @EN("No room for goods")
    private static Lang status_no_space_for_goods;

    @CN("需要输入：GTCEu能量")
    @EN("Needs input: GTCEu Energy")
    private static Lang status_no_eu;

    @CN("需要输入：应力")
    @EN("Needs input: Stress")
    private static Lang status_no_stress;

    @CN("当前条件下无法交易")
    @EN("This trade is blocked")
    private static Lang status_blocked;

    /**
     * Outcome of a trade attempt, plus the machine's balance afterwards.
     * <p>
     * Successes carry the new balance; failures carry the balance unchanged so the caller can apply it blindly.
     */
    public record Attempt(Result result, long storedBalance) {

        static Attempt of(Result result, long stored) {
            return new Attempt(result, stored);
        }

        public boolean isSuccess() {
            return result == Result.SUCCESS;
        }
    }

    /** Outcome of a trade attempt. */
    public enum Result {

        /** A trade was settled. */
        SUCCESS,
        /** The ticket has no readable entries. */
        NO_ENTRIES,
        /** No line on the ticket has quota left. */
        EXHAUSTED,
        /** The machine cannot pay for the goods. */
        MACHINE_CANNOT_PAY,
        /** The machine has no stock of the goods to hand over. */
        MACHINE_OUT_OF_STOCK,
        /** The goods would not fit in the machine's bin. */
        NO_SPACE_FOR_GOODS,
        /** An electric machine has no EU to draw from. */
        NO_EU,
        /** A kinetic machine has no live, turning Create network. */
        NO_STRESS,
        /** This line could not trade in either allowed direction. */
        BLOCKED;

        /** Player-facing description. */
        public MutableComponent describe() {
            return switch (this) {
                case SUCCESS -> status_success.translate();
                case NO_ENTRIES -> status_no_entries.translate();
                case EXHAUSTED -> status_exhausted.translate();
                case MACHINE_CANNOT_PAY -> status_machine_cannot_pay.translate();
                case MACHINE_OUT_OF_STOCK -> status_machine_out_of_stock.translate();
                case NO_SPACE_FOR_GOODS -> status_no_space_for_goods.translate();
                case NO_EU -> status_no_eu.translate();
                case NO_STRESS -> status_no_stress.translate();
                case BLOCKED -> status_blocked.translate();
            };
        }
    }

    /**
     * Settles the first ticket line that can trade.
     *
     * @param ticketStack mutated on success; write it back to the originating slot
     * @param goods       the bin trades read from and write to
     * @param stored      the machine's money, already including anything its coin slots held
     * @param prices      resolves what each line actually charges, so pricing stays swappable
     * @param level       the machine's level, passed through to the resolver; may be null
     * @return the outcome, with {@link Attempt#storedBalance()} holding the new balance on success
     */
    public static Attempt tryTrade(ItemStack ticketStack, GoodsAccess goods, long stored,
                                   PriceResolver prices, Level level) {
        return tryTrade(ticketStack, goods, stored, prices, level, false);
    }

    /**
     * Reports whether this ticket could settle right now, without settling it.
     * <p>
     * The machine uses this to decide whether to charge up a trade at all: without it the progress bar would fill
     * against work that cannot be done, then snap back to empty and start over.
     *
     * @return {@link Result#SUCCESS} if a line would settle, otherwise why none would
     */
    public static Result probe(ItemStack ticketStack, GoodsAccess goods, long stored,
                               PriceResolver prices, Level level) {
        return tryTrade(ticketStack, goods, stored, prices, level, true).result();
    }

    /**
     * @param simulate when true, report what would happen without moving goods or spending quota; the returned
     *                 balance is the one a real trade would leave
     */
    public static Attempt tryTrade(ItemStack ticketStack, GoodsAccess goods, long stored,
                                   PriceResolver prices, Level level, boolean simulate) {
        List<TradeEntry> entries = MarketTicket.getEntries(ticketStack);
        if (entries.isEmpty()) {
            return Attempt.of(Result.NO_ENTRIES, stored);
        }
        Result last = Result.EXHAUSTED;
        for (int index = 0; index < entries.size(); index++) {
            TradeEntry entry = entries.get(index);
            if (!entry.hasQuotaLeft()) {
                continue;
            }
            // Selling is tried first: it costs stock, not coins.
            Attempt attempt = trySell(entry, goods, stored, prices, level, simulate);
            if (attempt.result() == Result.BLOCKED) {
                attempt = tryBuy(entry, goods, stored, prices, level, simulate);
            }
            if (attempt.result() == Result.BLOCKED) {
                last = Result.BLOCKED;
                continue;
            }
            if (attempt.isSuccess() && !simulate) {
                commitQuota(ticketStack, index, entry);
            }
            return attempt;
        }
        return Attempt.of(last, stored);
    }

    /** SELL: goods out, money in. */
    private static Attempt trySell(TradeEntry entry, GoodsAccess goods, long stored,
                                   PriceResolver prices, Level level, boolean simulate) {
        if (!entry.restriction().allowsSell()) {
            return Attempt.of(Result.BLOCKED, stored);
        }
        long amount = entry.tradeAmount();
        long extracted = goods.extract(entry.content(), amount, true);
        if (extracted < amount) {
            return Attempt.of(Result.MACHINE_OUT_OF_STOCK, stored);
        }
        if (!simulate) {
            goods.extract(entry.content(), amount, false);
        }
        return Attempt.of(Result.SUCCESS, stored + prices.buyPrice(level, entry));
    }

    /** BUY: goods in, money out. Stock space and funds are both checked before anything moves. */
    private static Attempt tryBuy(TradeEntry entry, GoodsAccess goods, long stored,
                                  PriceResolver prices, Level level, boolean simulate) {
        if (!entry.restriction().allowsBuy()) {
            return Attempt.of(Result.BLOCKED, stored);
        }
        long amount = entry.tradeAmount();
        long price = prices.sellPrice(level, entry);
        if (stored < price) {
            return Attempt.of(Result.MACHINE_CANNOT_PAY, stored);
        }
        if (goods.insert(entry.content(), amount, true) < amount) {
            return Attempt.of(Result.NO_SPACE_FOR_GOODS, stored);
        }
        if (!simulate) {
            goods.insert(entry.content(), amount, false);
        }
        return Attempt.of(Result.SUCCESS, stored - price);
    }

    /** Decrements the quota; a negative count means unlimited. */
    private static void commitQuota(ItemStack ticketStack, int index, TradeEntry entry) {
        int remaining = entry.remainingTrades();
        if (remaining > 0) {
            remaining -= 1;
        }
        MarketTicket.updateEntry(ticketStack, index, entry.withRemainingTrades(remaining));
    }
}
