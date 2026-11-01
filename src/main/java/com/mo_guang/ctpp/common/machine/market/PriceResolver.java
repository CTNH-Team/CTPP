package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.world.level.Level;

/**
 * Turns a ticket line into the price actually charged for one trade.
 * <p>
 * Tickets carry a {@code coin} value, but nothing guarantees that value is what gets paid: a future supply-and-demand
 * model would scale it by market conditions. Every price consumer goes through this interface so that change stays
 * confined to one implementation rather than being threaded through the executor and the GUI.
 */
public interface PriceResolver {

    /** Charges exactly what the ticket says. */
    PriceResolver FIXED = new PriceResolver() {

        @Override
        public long buyPrice(Level level, TradeEntry entry) {
            return entry.ticketPrice();
        }

        @Override
        public long sellPrice(Level level, TradeEntry entry) {
            return entry.ticketPrice();
        }
    };

    /**
     * What the machine pays the player for one unit of {@code entry}.
     *
     * @param level the machine's level, or null when resolving outside a world
     */
    long buyPrice(Level level, TradeEntry entry);

    /** What the machine charges the player for one unit of {@code entry}. */
    long sellPrice(Level level, TradeEntry entry);
}
