package com.mo_guang.ctpp.common.machine.market;

import org.jetbrains.annotations.Nullable;

/**
 * Which side of a trade the ticket allows.
 * <p>
 * The NBT names are the ones the ticket format documents ({@code "buy_only"} / {@code "sell_only"} / absent), so
 * they must not be renamed casually: existing tickets in the world carry these strings.
 */
public enum TradeRestriction {

    /** Both directions are allowed. */
    BOTH(null),
    /** The machine buys from the player: goods go in, money comes out. */
    BUY_ONLY("buy_only"),
    /** The machine sells to the player: money goes in, goods come out. */
    SELL_ONLY("sell_only");

    @Nullable
    private final String tagName;

    TradeRestriction(@Nullable String tagName) {
        this.tagName = tagName;
    }

    @Nullable
    public String getTagName() {
        return tagName;
    }

    public boolean allowsBuy() {
        return this != SELL_ONLY;
    }

    public boolean allowsSell() {
        return this != BUY_ONLY;
    }

    public static TradeRestriction fromTagName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return BOTH;
        }
        for (TradeRestriction restriction : values()) {
            if (name.equals(restriction.tagName)) {
                return restriction;
            }
        }
        return BOTH;
    }
}
