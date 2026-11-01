package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads, plans and moves CTPP coins inside an item handler.
 * <p>
 * All arithmetic is done in nuggets ({@link CoinDenomination#COPPER} units, i.e. 9^0). Because every rank is a
 * power of nine, a top-down greedy pass always terminates at an exact amount: the worst case just falls back to
 * nuggets, which are worth 1.
 */
public final class CurrencyUtil {

    private CurrencyUtil() {}

    /** One line of a coin plan: how many coins of a given rank to move. */
    public record CoinStack(CoinDenomination denomination, int count) {

        public long value() {
            return denomination.getValue() * count;
        }
    }

    /**
     * @return the total value held in the handler, in nuggets.
     */
    public static long getBalance(IItemHandler handler) {
        long balance = 0L;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CoinDenomination denomination = CoinDenominationRegistry.fromStack(stack);
            if (denomination != null) {
                balance += denomination.getValue() * stack.getCount();
            }
        }
        return balance;
    }

    /** How many single coins of each rank are currently available. */
    private static int[] countAvailable(IItemHandler handler) {
        int[] available = new int[CoinDenomination.rankCount()];
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CoinDenomination denomination = CoinDenominationRegistry.fromStack(stack);
            if (denomination != null) {
                available[denomination.getRank()] += stack.getCount();
            }
        }
        return available;
    }

    public static boolean canAfford(IItemHandler handler, long amount) {
        return amount <= 0 || getBalance(handler) >= amount;
    }

    /**
     * Works out which physical coins to take to pay {@code amount}, preferring the largest coins available.
     *
     * @return an exact plan, or {@code null} when the handler cannot cover the amount.
     */
    public static List<CoinStack> planPayment(IItemHandler handler, long amount) {
        if (amount <= 0) {
            return Collections.emptyList();
        }
        int[] available = countAvailable(handler);
        List<CoinStack> plan = new ArrayList<>();
        long rest = amount;
        for (int rank = CoinDenomination.rankCount() - 1; rank >= 0; rank--) {
            long value = CoinDenomination.byRank(rank).getValue();
            int take = (int) Math.min(rest / value, available[rank]);
            if (take > 0) {
                plan.add(new CoinStack(CoinDenomination.byRank(rank), take));
                rest -= value * take;
            }
        }
        return rest == 0 ? plan : null;
    }

    /**
     * Removes the given plan from the handler. Callers must run {@link #planPayment} first; this method assumes the
     * coins are still there, so check its return value (the value actually withdrawn) before granting goods.
     *
     * @return the value actually removed, in nuggets.
     */
    public static long withdraw(IItemHandlerModifiable handler, List<CoinStack> plan) {
        long withdrawn = 0L;
        for (CoinStack line : plan) {
            int remaining = line.count();
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || CoinDenominationRegistry.fromStack(stack) != line.denomination()) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                ItemStack extracted = handler.extractItem(slot, take, false);
                withdrawn += line.denomination().getValue() * extracted.getCount();
                remaining -= extracted.getCount();
            }
        }
        return withdrawn;
    }

    /**
     * Inserts coins worth {@code amount} into the handler, breaking larger coins down when a slot cannot take them.
     *
     * @param simulate when true nothing is actually inserted
     * @return the value that could <em>not</em> be inserted, in nuggets.
     */
    public static long deposit(IItemHandler handler, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0L;
        }
        long rest = amount;
        for (int rank = CoinDenomination.rankCount() - 1; rank >= 0 && rest > 0; rank--) {
            CoinDenomination denomination = CoinDenomination.byRank(rank);
            long value = denomination.getValue();
            long wanted = rest / value;
            if (wanted <= 0) {
                continue;
            }
            long inserted = insertCoins(handler, denomination, wanted, simulate);
            rest -= inserted * value;
        }
        return rest;
    }

    private static long insertCoins(IItemHandler handler, CoinDenomination denomination, long count, boolean simulate) {
        long inserted = 0L;
        int remaining = (int) Math.min(count, Integer.MAX_VALUE);
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            int chunk = Math.min(remaining, denomination.getItem().getMaxStackSize());
            ItemStack probe = new ItemStack(denomination.getItem(), chunk);
            ItemStack leftover = handler.insertItem(slot, probe, simulate);
            int moved = chunk - leftover.getCount();
            if (moved > 0) {
                inserted += moved;
                remaining -= moved;
            }
        }
        return inserted;
    }

    /**
     * Compact display such as {@code 3⬢ 2◇ 5○}; empty values are skipped.
     * <p>
     * The ○/◇/⬢ marks repeat every three ranks, so higher orders carry a digit prefix for the place they occupy:
     * the same numeral set reading as {@code 1₂○ 3⬢ 3◇ 1○} means "one scalar-prefixed nugget-rank coin", i.e. rank
     * 3. Without that, {@code 1○ … 1○} would be ambiguous, and rank 3 and rank 0 would print identically.
     */
    public static String format(long amount) {
        if (amount <= 0) {
            return "0○";
        }
        StringBuilder builder = new StringBuilder();
        long rest = amount;
        for (int rank = CoinDenomination.rankCount() - 1; rank >= 0; rank--) {
            CoinDenomination denomination = CoinDenomination.byRank(rank);
            long count = rest / denomination.getValue();
            if (count > 0) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(count).append(orderPrefix(rank)).append(denomination.getMark());
                rest -= count * denomination.getValue();
            }
        }
        return builder.isEmpty() ? "0○" : builder.toString();
    }

    /**
     * Ranks 0-2 are the familiar nugget / ingot / block. From rank 3 up the mark alone would repeat, so each order
     * gets a subscript digit of how many base-9 groups it sits above.
     */
    private static String orderPrefix(int rank) {
        if (rank < 3) {
            return "";
        }
        return switch (rank / 3) {
            case 1 -> "²";
            case 2 -> "³";
            default -> "⁴";
        } + "";
    }

    /** A purse made only of nuggets, used to describe plain values in tooltips. */
    public static long valueOf(ItemStack stack) {
        CoinDenomination denomination = CoinDenominationRegistry.fromStack(stack);
        return denomination == null ? 0L : denomination.getValue() * stack.getCount();
    }
}
