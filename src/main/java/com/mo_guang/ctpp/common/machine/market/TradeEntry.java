package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.ctnhlang.CN;
import com.ctnhlang.Category;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import org.jetbrains.annotations.Nullable;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

/**
 * One immutable snapshot of a line item read out of a market ticket.
 * <p>
 * Tickets live in NBT, so every read hands back a fresh instance rather than a live view; mutating a ticket means
 * writing the entry back through {@link MarketTicket#updateEntry}.
 */
@Prefix("market")
@Category("machine")
public record TradeEntry(AEKey content, long price, TradeRestriction restriction, int remainingTrades) {

    @CN("%s，单价 %s/%s，%s，剩余 %s 次")
    @EN("%s at %s per %s, %s, %s trades left")
    private static Lang entry_desc;

    @CN("个")
    @EN("item")
    private static Lang entry_per_item;

    @CN("mB")
    @EN("mB")
    private static Lang entry_per_bucket;

    @CN("仅收购")
    @EN("machine buys only")
    private static Lang restriction_buy_only;

    @CN("仅出售")
    @EN("machine sells only")
    private static Lang restriction_sell_only;

    @CN("双向")
    @EN("both directions")
    private static Lang restriction_both;

    @CN("无限")
    @EN("unlimited")
    private static Lang entry_unlimited;

    /**
     * Short description for tooltips: the goods, the unit price, the allowed direction and the remaining quota.
     * <p>
     * The price is quoted per single item or per millibucket, and the quota counts trades, so the two agree: a
     * quota of 40 at price 3 buys 40 items for 120.
     */
    public MutableComponent describe() {
        return entry_desc.translate(
                content.getDisplayName(),
                CurrencyUtil.format(price),
                isFluid() ? entry_per_bucket : entry_per_item,
                restrictionName(),
                remainingTrades < 0 ? entry_unlimited.translate() :
                        Component.literal(Integer.toString(remainingTrades)));
    }

    /** Human-readable direction, e.g. "仅收购". */
    public Component restrictionName() {
        return switch (restriction) {
            case BOTH -> restriction_both.translate();
            case BUY_ONLY -> restriction_buy_only.translate();
            case SELL_ONLY -> restriction_sell_only.translate();
        };
    }

    /** A representative 16x16 icon for GUI slots; fluid keys render through AE2 instead. */
    @Nullable
    public ItemStack icon() {
        if (content instanceof AEItemKey itemKey) {
            return itemKey.getReadOnlyStack();
        }
        return null;
    }

    public boolean isFluid() {
        return content instanceof AEFluidKey;
    }

    public boolean hasQuotaLeft() {
        return remainingTrades < 0 || remainingTrades > 0;
    }

    public TradeEntry withRemainingTrades(int remaining) {
        return new TradeEntry(content, price, restriction, remaining);
    }

    /**
     * The smallest goods unit this entry trades in: one item, or one millibucket of fluid.
     * <p>
     * Both are exactly 1, so the goods amount and the price are always in the same 1:1 unit — the ticket's
     * {@code coin} value is literally "price per item" or "price per mB". Keeping the unit at 1 means a quota of
     * {@code n} always buys exactly {@code n} items no matter what, with no rounding anywhere.
     */
    public static final long UNIT_AMOUNT = 1L;

    /** How many goods one trade moves. */
    public long tradeAmount() {
        return UNIT_AMOUNT;
    }

    /** The price of one trade, i.e. the price of a single item or a single mB. */
    public long tradePrice() {
        return price;
    }

    //////////////////////////////////////
    // ***** NBT *****//
    //////////////////////////////////////

    public static final String TAG_CONTENT = "content";
    public static final String TAG_COIN = "coin";
    public static final String TAG_RESTRICTION = "restriction";
    public static final String TAG_REMAINING = "remaining_trade_count";

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_CONTENT, content.toTagGeneric());
        tag.putLong(TAG_COIN, price);
        if (restriction.getTagName() != null) {
            tag.putString(TAG_RESTRICTION, restriction.getTagName());
        }
        tag.putInt(TAG_REMAINING, remainingTrades);
        return tag;
    }

    @Nullable
    public static TradeEntry fromTag(CompoundTag tag) {
        if (!tag.contains(TAG_CONTENT) || !tag.contains(TAG_COIN)) {
            return null;
        }
        AEKey content = AEKey.fromTagGeneric(tag.getCompound(TAG_CONTENT));
        if (content == null) {
            return null;
        }
        long price = tag.getLong(TAG_COIN);
        if (price <= 0) {
            return null;
        }
        TradeRestriction restriction = TradeRestriction.fromTagName(tag.getString(TAG_RESTRICTION));
        int remaining = tag.contains(TAG_REMAINING) ? tag.getInt(TAG_REMAINING) : -1;
        return new TradeEntry(content, price, restriction, remaining);
    }

    /** Rank 0 coin constant used when a caller needs a stable "cheapest coin" reference. */
    public static final CoinDenomination BASE_COIN = CoinDenomination.COPPER;
}
