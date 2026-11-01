package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.Locale;

/**
 * Coin handling for the market.
 * <p>
 * A machine holds money in two places: a numeric balance, and the coins physically sitting in its purse slots. The
 * balance is only the part the purse does <em>not</em> currently show, so the machine's real total is the two added
 * together ({@link #total}). Keeping them disjoint is what stops money being counted twice or vanishing: a coin is
 * either a number or an item, never both.
 * <p>
 * That split means trading never has to touch a slot. A trade reads the total, adjusts the number, and
 * {@link #project} then redraws the purse to match — so a machine is free to pay amounts smaller than its smallest
 * coin, and a purse of eight slots can hold more than eight slots' worth.
 * <p>
 * All amounts are nuggets. Every denomination is a power of nine, so the projection is exact: nothing here rounds.
 */
public final class CurrencyUtil {

    /** Ranks that a payout will try, largest first. */
    private static final int TOP_RANK = CoinDenomination.rankCount() - 1;

    private CurrencyUtil() {}

    /** Total value of the coins currently in the handler, in nuggets. */
    public static long getBalance(IItemHandler handler) {
        long balance = 0L;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            balance += valueOf(handler.getStackInSlot(slot));
        }
        return balance;
    }

    /** Face value of one coin stack, in nuggets. */
    public static long valueOf(ItemStack stack) {
        CoinDenomination denomination = CoinDenominationRegistry.fromStack(stack);
        return denomination == null ? 0L : denomination.getValue() * stack.getCount();
    }

    /**
     * Everything the machine owns: the balance plus the coins on display.
     * <p>
     * This is the figure the GUI shows and the only one worth comparing against a price. Money a player has just
     * dropped into the purse is already included, so a deposit needs no separate step to be spendable.
     */
    public static long total(IItemHandler handler, long stored) {
        return stored + getBalance(handler);
    }

    /**
     * Redraws the purse so it shows {@code total} in the fewest coins, keeping whatever does not fit.
     * <p>
     * Overflow is expected rather than an error: eight slots can only show so much, and the rest stays in the
     * balance where trading can still spend it and {@link #total} still counts it.
     * <p>
     * Only slots whose contents actually change are written, so a machine that is merely idling does not dirty its
     * chunk every tick.
     *
     * @return the part of {@code total} not represented by coins, to keep as the stored balance
     */
    public static long project(IItemHandlerModifiable handler, long total) {
        long[] layout = decompose(handler, total);
        long placed = 0L;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack current = handler.getStackInSlot(slot);
            ItemStack wanted = stackFor(layout, slot);
            placed += valueOf(wanted);
            if (!ItemStack.matches(current, wanted)) {
                handler.setStackInSlot(slot, wanted);
            }
        }
        return total - placed;
    }

    /**
     * Greedy top-down fill of the slots, largest rank first.
     * <p>
     * Ranks are powers of nine, so this is exact; only the tail can fail to fit, and that is what the caller keeps
     * as a stored balance. Results are indexed by slot so they can be applied as a diff. Each entry packs the rank
     * in the low byte and the stack count above it.
     */
    private static long[] decompose(IItemHandlerModifiable handler, long total) {
        long[] layout = new long[handler.getSlots()];
        long remaining = total;
        int slot = 0;
        for (int rank = TOP_RANK; rank >= 0 && remaining > 0 && slot < layout.length; rank--) {
            CoinDenomination denomination = CoinDenomination.byRank(rank);
            long unit = denomination.getValue();
            long needed = remaining / unit;
            int stackSize = Math.max(1, denomination.getStack(1).getMaxStackSize());
            while (needed > 0 && slot < layout.length) {
                long count = Math.min(needed, stackSize);
                layout[slot++] = (count << 8) | rank;
                needed -= count;
                remaining -= count * unit;
            }
        }
        return layout;
    }

    /** Turns the packed layout entry for one slot back into the stack it stands for. */
    private static ItemStack stackFor(long[] layout, int slot) {
        long packed = layout[slot];
        if (packed == 0L) {
            return ItemStack.EMPTY;
        }
        return CoinDenomination.byRank((int) (packed & 0xFF)).getStack((int) (packed >>> 8));
    }

    /** Scale suffix for a three-digit group, indexed by that group's power of a thousand. */
    private static final String[] SCALE_SUFFIXES = { "", "K", "M", "B", "T", "Q" };

    /** Marks for the three displayed groups, most significant first. */
    private static final String[] GROUP_MARKS = {
            CoinDenomination.BLOCK.getMark(),
            CoinDenomination.INGOT.getMark(),
            CoinDenomination.NUGGET.getMark()
    };

    /** Below this, the value is small enough to name the coins it literally is. */
    private static final long GROUPED_FROM = 1_000L;

    /** Highest coin rank the compact display names; above it the marks would repeat and read ambiguously. */
    private static final int DISPLAY_TOP_RANK = CoinDenomination.BLOCK.getRank();

    /**
     * Compact display of an amount.
     * <p>
     * Under a thousand the amount is spelled out as the coins it decomposes into. Past that it is read the way a
     * long number normally is: split into three-digit groups from the right, printing at most the three highest.
     * Each printed group takes the mark for its rank within that display (block, then ingot, then nugget) and the
     * scale suffix for its actual magnitude, so {@code 1128256} reads {@code 1.0M⬢ 128.0K◇ 256○} and
     * {@code 1128256000} reads {@code 1.0B⬢ 128.0M◇ 256.0K○}.
     * <p>
     * Any group carrying a suffix keeps one decimal. That is what tells the player the figure is rounded rather
     * than exact.
     */
    public static String format(long amount) {
        if (amount <= 0) {
            return "0○";
        }
        if (amount < GROUPED_FROM) {
            return spellOut(amount);
        }
        // Every three-digit group, least significant first.
        long[] groups = new long[SCALE_SUFFIXES.length];
        long rest = amount;
        for (int index = 0; index < groups.length; index++) {
            groups[index] = rest % 1000L;
            rest /= 1000L;
        }
        // Anything above the table folds into the top group, so an enormous balance still reads as a number.
        groups[groups.length - 1] += rest * 1000L;

        int highest = groups.length - 1;
        while (highest > 0 && groups[highest] == 0L) {
            highest--;
        }
        // Three marks to spend, so the lowest of a taller number is simply left out.
        int lowest = Math.max(0, highest - (GROUP_MARKS.length - 1));

        StringBuilder builder = new StringBuilder();
        for (int index = highest; index >= lowest; index--) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            int position = highest - index;
            // Only the nugget group splits its decimal: it is fine enough that the leftover nuggets in the group
            // below are worth showing. Blocks and ingots stay whole numbers, where a fraction would just add digits.
            boolean carriesRemainder = index != 0 && position == GROUP_MARKS.length - 1;
            String number = carriesRemainder ? withRemainder(groups, index) : Long.toString(groups[index]);
            builder.append(number)
                    .append(SCALE_SUFFIXES[index])
                    .append(GROUP_MARKS[position]);
        }
        return builder.toString();
    }

    /**
     * Renders one group with the leading digit of the group beneath it as a single decimal, so {@code 990} over
     * {@code 144} reads {@code 990.1}.
     * <p>
     * That lower group is part of the total and would otherwise be dropped entirely by the three-mark limit, so
     * folding its leading digit into the decimal keeps the figure from understating itself. One place is enough to
     * show the magnitude; the rest of the group is what the next segment, when there is one, spells out.
     */
    private static String withRemainder(long[] groups, int index) {
        return groups[index] + "." + (groups[index - 1] / 100L);
    }

    /**
     * Coin breakdown such as {@code 9⬢ 1◇ 2○}; empty ranks are skipped.
     * <p>
     * Stops at the block rank and lets its count run past a full stack, rather than climbing into the gold rank and
     * beyond. Those higher ranks reuse the same three marks, so naming them would print a gold coin as {@code ○} and
     * be indistinguishable from a nugget.
     */
    private static String spellOut(long amount) {
        StringBuilder builder = new StringBuilder();
        long rest = amount;
        for (int rank = DISPLAY_TOP_RANK; rank >= 0; rank--) {
            CoinDenomination denomination = CoinDenomination.byRank(rank);
            long count = rest / denomination.getValue();
            if (count > 0) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(count).append(denomination.getMark());
                rest -= count * denomination.getValue();
            }
        }
        return builder.isEmpty() ? "0○" : builder.toString();
    }

    /** One decimal place, trailing ".0" kept so the coin tooltip rows line up. */
    public static String trim(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
