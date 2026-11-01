package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ticket NBT helpers.
 *
 * <pre>
 * { "receite": { "entries": [ { "content": {...aeKey}, "coin": 7, "restriction": "buy_only",
 *                               "remaining_trade_count": 12 } ] } }
 * </pre>
 */
public final class MarketTicket {

    /** Outer tag; keeps the "receite" spelling used by the original design notes. */
    public static final String TAG_RECEIPT = "receite";
    public static final String TAG_ENTRIES = "entries";

    private MarketTicket() {}

    public static boolean isTicket(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().contains(TAG_RECEIPT);
    }

    /**
     * Readable entries; malformed ones are skipped. Accepts either an {@code entries} list or a single entry's
     * fields written directly under {@code receite}.
     */
    public static List<TradeEntry> getEntries(ItemStack stack) {
        if (!stack.hasTag()) {
            return Collections.emptyList();
        }
        CompoundTag receipt = stack.getTag().getCompound(TAG_RECEIPT);
        if (receipt.isEmpty()) {
            return Collections.emptyList();
        }
        if (!receipt.contains(TAG_ENTRIES)) {
            TradeEntry single = TradeEntry.fromTag(receipt);
            return single == null ? Collections.emptyList() : List.of(single);
        }
        ListTag list = receipt.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        List<TradeEntry> entries = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            TradeEntry entry = TradeEntry.fromTag(list.getCompound(i));
            if (entry != null) {
                entries.add(entry);
            }
        }
        return Collections.unmodifiableList(entries);
    }

    @Nullable
    public static TradeEntry getEntry(ItemStack stack, int index) {
        List<TradeEntry> entries = getEntries(stack);
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    public static int entryCount(ItemStack stack) {
        return getEntries(stack).size();
    }

    /**
     * Replaces the whole entry list. This mutates {@code stack} in place and returns it for convenience, so callers
     * holding a slot's stack can simply write it back.
     */
    public static ItemStack setEntries(ItemStack stack, List<TradeEntry> entries) {
        CompoundTag receipt = stack.getOrCreateTagElement(TAG_RECEIPT);
        ListTag list = new ListTag();
        for (TradeEntry entry : entries) {
            list.add(entry.toTag());
        }
        receipt.put(TAG_ENTRIES, list);
        return stack;
    }

    public static ItemStack create(ItemStack ticket, List<TradeEntry> entries) {
        return setEntries(ticket.copyWithCount(1), entries);
    }

    /** Rewrites one entry, keeping the rest of the ticket untouched. */
    public static ItemStack updateEntry(ItemStack stack, int index, TradeEntry entry) {
        List<TradeEntry> entries = new ArrayList<>(getEntries(stack));
        if (index < 0 || index >= entries.size()) {
            return stack;
        }
        entries.set(index, entry);
        return setEntries(stack, entries);
    }

    /** Adds an entry to the ticket; returns false when the hard cap is reached. */
    public static boolean addEntry(ItemStack stack, TradeEntry entry) {
        List<TradeEntry> entries = new ArrayList<>(getEntries(stack));
        if (entries.size() >= MAX_ENTRIES) {
            return false;
        }
        entries.add(entry);
        setEntries(stack, entries);
        return true;
    }

    public static boolean removeEntry(ItemStack stack, int index) {
        List<TradeEntry> entries = new ArrayList<>(getEntries(stack));
        if (index < 0 || index >= entries.size()) {
            return false;
        }
        entries.remove(index);
        setEntries(stack, entries);
        return true;
    }

    /** A ticket is "spent" once every line has run out of quota or is otherwise unusable. */
    public static boolean isExhausted(ItemStack stack) {
        List<TradeEntry> entries = getEntries(stack);
        if (entries.isEmpty()) {
            return false;
        }
        for (TradeEntry entry : entries) {
            if (entry.hasQuotaLeft()) {
                return false;
            }
        }
        return true;
    }

    /** Upper bound on lines per ticket, to keep a single stack's NBT small. */
    public static final int MAX_ENTRIES = 8;
}
