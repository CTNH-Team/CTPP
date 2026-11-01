package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.mo_guang.ctpp.registry.CTPPMarketItems;

/**
 * The currency ladder used by the CTPP market.
 * <p>
 * Every rank is worth exactly nine of the rank below it, so the whole ladder is a canonical base-9 numeral
 * system. That property is what lets {@link CurrencyUtil} break an arbitrary amount into exact change with a
 * greedy decomposition: a base-9 digit is at most 8, so a single stack always holds every coin of one rank.
 * <p>
 * Rank 0 is "one nugget", rank 1 is "one ingot" (9 nuggets), rank 2 is "one block" (9 ingots). The
 * nugget/ingot/block wording and the ○/◇/⬢ marks repeat every three ranks.
 */
public enum CoinDenomination {

    COPPER("copper_coin", "铜币", "Copper Coin"),
    CUPRONICKEL("cupronickel_coin", "白铜币", "Cupronickel Coin"),
    SILVER("silver_coin", "银币", "Silver Coin"),
    GOLD("gold_coin", "金币", "Gold Coin"),
    PLATINUM("platinum_coin", "铂币", "Platinum Coin"),
    OSMIUM("osmium_coin", "锇币", "Osmium Coin"),
    NAQUADAH("naquadah_coin", "硅岩币", "Naquadah Coin"),
    NEUTRONIUM("neutronium_coin", "中子素币", "Neutronium Coin");

    /** Coins are ordinary items; a full stack is 64. */
    public static final int MAX_STACK = 64;

    /** Marks repeat every three ranks: nugget ○, ingot ◇, block ⬢. */
    private static final String[] MARKS = { "○", "◇", "⬢" };

    /*
     * Tooltip colours, sampled from GT's own credit textures so a coin's tooltip matches the coin it describes:
     * copper's warm orange, cupronickel's pale brass, and silver's cool white-grey.
     */
    public static final int COLOR_NUGGET = 0xE77C56;
    public static final int COLOR_INGOT = 0xD8B47D;
    public static final int COLOR_BLOCK = 0xD9D9FC;

    private static final long[] VALUES = new long[values().length];

    static {
        long value = 1L;
        for (CoinDenomination denomination : values()) {
            VALUES[denomination.ordinal()] = value;
            value *= 9L;
        }
    }

    private final String itemId;
    private final String cnName;
    private final String enName;

    CoinDenomination(String itemId, String cnName, String enName) {
        this.itemId = itemId;
        this.cnName = cnName;
        this.enName = enName;
    }

    public String getItemId() {
        return itemId;
    }

    public String getCnName() {
        return cnName;
    }

    public String getEnName() {
        return enName;
    }

    /** How many nuggets one coin of this rank is worth. */
    public long getValue() {
        return VALUES[ordinal()];
    }

    /** ○ / ◇ / ⬢ — the shorthand mark shown in tooltips and the GUI. */
    public String getMark() {
        return MARKS[ordinal() % MARKS.length];
    }

    /**
     * Which of the three visible denominations this rank is worth, for colouring: ranks 0-2 are the copper nugget,
     * the cupronickel ingot and the silver block, and everything above them inherits the same three colours so a
     * gold coin still reads as "an ingot-ish amount".
     */
    public int getColour() {
        return switch (ordinal() % MARKS.length) {
            case 0 -> COLOR_NUGGET;
            case 1 -> COLOR_INGOT;
            default -> COLOR_BLOCK;
        };
    }

    /*
     * The three displayable units. Values are shown with the marks from the specification — ○ for nuggets,
     * ◇ for ingots, ⬢ for blocks — so a tooltip reads "3 ⬢" rather than "3 blk".
     */
    public static final CoinDenomination NUGGET = values()[0];
    public static final CoinDenomination INGOT = values()[1];
    public static final CoinDenomination BLOCK = values()[2];

    /**
     * The natural unit for a value: blocks from 81 nuggets up, ingots from 9, otherwise nuggets.
     * <p>
     * Thresholds are deliberately "1 block" and "1 ingot" rather than three-of-a-kind, so a value is always shown
     * in the largest unit it can fill at least one whole unit of.
     */
    public static CoinDenomination unitFor(long amount) {
        if (amount >= BLOCK.getValue()) {
            return BLOCK;
        }
        if (amount >= INGOT.getValue()) {
            return INGOT;
        }
        return NUGGET;
    }

    public int getRank() {
        return ordinal();
    }

    /**
     * Resolves the registered coin item. This goes through {@link CTPPMarketItems} lazily rather than holding a
     * static reference so that the enum can be loaded before registrate has run.
     */
    public Item getItem() {
        return CTPPMarketItems.getCoinItem(this);
    }

    public ItemStack getStack(int count) {
        return new ItemStack(getItem(), count);
    }

    public static CoinDenomination byRank(int rank) {
        CoinDenomination[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, rank))];
    }

    public static int rankCount() {
        return values().length;
    }

    /** The most valuable coin rank that is still worth no more than {@code amount}. */
    public static CoinDenomination largestFitting(long amount) {
        CoinDenomination[] all = values();
        for (int i = all.length - 1; i > 0; i--) {
            if (all[i].getValue() <= amount) {
                return all[i];
            }
        }
        return COPPER;
    }

    /**
     * The largest amount that the whole ladder can represent, i.e. one full stack of the top coin. Anything above
     * this has to be split across several stacks.
     */
    public static long maxSingleStackValue() {
        return values()[values().length - 1].getValue() * MAX_STACK;
    }
}
