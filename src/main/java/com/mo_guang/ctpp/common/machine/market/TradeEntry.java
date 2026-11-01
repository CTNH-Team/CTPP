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

/** Immutable snapshot of one ticket line. Mutate via {@link MarketTicket#updateEntry}. */
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

    /** Tooltip line: goods, unit price, direction and remaining quota. */
    public MutableComponent describe() {
        return describe(price);
    }

    /**
     * Same line, but quoting {@code chargedPrice} instead of the ticket's own.
     * <p>
     * The GUI resolves prices through {@link PriceResolver}, so what an entry reads as and what it actually costs
     * stay in step once pricing stops being fixed.
     */
    public MutableComponent describe(long chargedPrice) {
        return entry_desc.translate(
                content.getDisplayName(),
                CurrencyUtil.format(chargedPrice),
                isFluid() ? entry_per_bucket.translate() : entry_per_item.translate(),
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

    /** Item icon for GUI slots; fluids render through AE2. */
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

    /** One item or one millibucket: price and amount share this 1:1 unit, so no rounding occurs. */
    public static final long UNIT_AMOUNT = 1L;

    /** How many goods one trade moves. */
    public long tradeAmount() {
        return UNIT_AMOUNT;
    }

    /**
     * The price written on the ticket itself, for one item or one millibucket.
     * <p>
     * This is the <em>asking</em> price, not necessarily what a trade settles at — {@link PriceResolver} decides
     * that, so a market-wide pricing model can override what a ticket asks for.
     */
    public long ticketPrice() {
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
