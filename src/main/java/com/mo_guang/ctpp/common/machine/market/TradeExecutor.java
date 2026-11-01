package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.ctnhlang.CN;
import com.ctnhlang.Category;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.util.List;

/** Settles one line of one ticket against the machine's goods and purse. Stateless. */
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

    @CN("金钱缓存已满")
    @EN("Money buffer full")
    private static Lang status_money_buffer_full;

    @CN("当前条件下无法交易")
    @EN("This trade is blocked")
    private static Lang status_blocked;

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
        /** The internal money buffer is full; selling stops. */
        MONEY_BUFFER_FULL,
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
                case MONEY_BUFFER_FULL -> status_money_buffer_full.translate();
                case BLOCKED -> status_blocked.translate();
            };
        }
    }

    /**
     * Settles the first ticket line that can trade.
     *
     * @param ticketStack     mutated on success; write it back to the originating slot
     * @param moneyBufferFull blocks selling only, since buying spends coins back out of the purse
     */
    public static Result tryTrade(ItemStack ticketStack, GoodsAccess goods, IItemHandler money,
                                  boolean moneyBufferFull) {
        List<TradeEntry> entries = MarketTicket.getEntries(ticketStack);
        if (entries.isEmpty()) {
            return Result.NO_ENTRIES;
        }
        Result last = Result.EXHAUSTED;
        for (int index = 0; index < entries.size(); index++) {
            TradeEntry entry = entries.get(index);
            if (!entry.hasQuotaLeft()) {
                continue;
            }
            // Selling is tried first: it costs stock, not coins.
            Result result = moneyBufferFull ? Result.BLOCKED : trySell(entry, goods, money);
            if (result == Result.BLOCKED) {
                result = tryBuy(entry, goods, money);
            }
            if (result == Result.BLOCKED) {
                last = moneyBufferFull ? Result.MONEY_BUFFER_FULL : Result.BLOCKED;
                continue;
            }
            if (result == Result.SUCCESS) {
                commitQuota(ticketStack, index, entry);
            }
            return result;
        }
        return last;
    }

    /** SELL: goods out, coins in. The purse must have room before stock moves. */
    private static Result trySell(TradeEntry entry, GoodsAccess goods, IItemHandler money) {
        if (!entry.restriction().allowsSell()) {
            return Result.BLOCKED;
        }
        long amount = entry.tradeAmount();
        long price = entry.tradePrice();
        if (goods.count(entry.content()) < amount) {
            return Result.MACHINE_OUT_OF_STOCK;
        }
        if (CurrencyUtil.deposit(money, price, true) > 0) {
            return Result.MACHINE_CANNOT_PAY;
        }
        long extracted = goods.extract(entry.content(), amount, false);
        if (extracted < amount) {
            return Result.MACHINE_OUT_OF_STOCK;
        }
        CurrencyUtil.deposit(money, price, false);
        return Result.SUCCESS;
    }

    /** BUY: goods in, coins out. Balance and receiving space are both checked first. */
    private static Result tryBuy(TradeEntry entry, GoodsAccess goods, IItemHandler money) {
        if (!entry.restriction().allowsBuy()) {
            return Result.BLOCKED;
        }
        long amount = entry.tradeAmount();
        long price = entry.tradePrice();
        if (CurrencyUtil.getBalance(money) < price) {
            return Result.MACHINE_CANNOT_PAY;
        }
        if (goods.insert(entry.content(), amount, true) < amount) {
            return Result.NO_SPACE_FOR_GOODS;
        }
        List<CurrencyUtil.CoinStack> plan = CurrencyUtil.planPayment(money, price);
        if (plan == null || !(money instanceof IItemHandlerModifiable modifiable)) {
            return Result.MACHINE_CANNOT_PAY;
        }
        long paid = CurrencyUtil.withdraw(modifiable, plan);
        if (paid != price) {
            // Coins changed between planning and withdrawal; put back what we took and bail out.
            refund(modifiable, paid);
            return Result.MACHINE_CANNOT_PAY;
        }
        long received = goods.insert(entry.content(), amount, false);
        if (received < amount) {
            refund(modifiable, paid);
            return Result.NO_SPACE_FOR_GOODS;
        }
        return Result.SUCCESS;
    }

    /**
     * Returns coins to the purse.
     * <p>
     * Every caller only reaches this after proving the purse had room for the same amount, so a shortfall means
     * the purse was mutated underneath us and the value is now unrecoverable. Failing loudly beats silently
     * deleting the player's money.
     */
    private static void refund(IItemHandlerModifiable money, long amount) {
        long leftover = CurrencyUtil.deposit(money, amount, false);
        if (leftover > 0) {
            throw new IllegalStateException(
                    "Market purse could not take back " + leftover + " nuggets of change; refusing to lose value");
        }
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
