package com.mo_guang.ctpp.registry;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.mo_guang.ctpp.common.item.CoinItem;
import com.mo_guang.ctpp.common.item.MarketTicketItem;
import com.mo_guang.ctpp.common.machine.market.CoinDenomination;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.mo_guang.ctpp.CTPPRegistration.REGISTRATE;

/**
 * Currency and ticket items for the market.
 * <p>
 * The coin art is GregTech's own {@code *_credit} texture set, which already ships with the vendored GTCEu, so no
 * new textures are needed for currency. The enum order in {@link CoinDenomination} matches GT's credit metals
 * exactly, which is why {@link #creditTexture} can walk the list positionally.
 */
public final class CTPPMarketItems {

    /** GT credit textures, in the same rank order as {@link CoinDenomination}. */
    private static final ResourceLocation[] CREDIT_TEXTURES = {
            GTCEu.id("item/copper_credit"),
            GTCEu.id("item/cupronickel_credit"),
            GTCEu.id("item/silver_credit"),
            GTCEu.id("item/gold_credit"),
            GTCEu.id("item/platinum_credit"),
            GTCEu.id("item/osmium_credit"),
            GTCEu.id("item/naquadah_credit"),
            GTCEu.id("item/neutronium_credit"),
    };

    private static final ItemEntry<?>[] COINS = new ItemEntry<?>[CoinDenomination.rankCount()];

    public static final ItemEntry<MarketTicketItem> MARKET_TICKET = REGISTRATE
            .item("market_ticket", MarketTicketItem::new)
            .cnlang("市场单据")
            .lang("Market Ticket")
            // Vanilla paper stands in until the ticket gets its own art.
            .model((ctx, prov) -> prov.generated(ctx, prov.mcLoc("item/paper")))
            .register();

    static {
        for (CoinDenomination denomination : CoinDenomination.values()) {
            COINS[denomination.getRank()] = REGISTRATE
                    .item(denomination.getItemId(), CoinItem::new)
                    .cnlang(denomination.getCnName())
                    .lang(denomination.getEnName())
                    .model((ctx, prov) -> prov.generated(ctx, creditTexture(denomination)))
                    .register();
        }
    }

    private CTPPMarketItems() {}

    /** Forces class loading so the coin items register alongside the rest of the module's items. */
    public static void init() {}

    private static ResourceLocation creditTexture(CoinDenomination denomination) {
        return CREDIT_TEXTURES[denomination.getRank() % CREDIT_TEXTURES.length];
    }

    @SuppressWarnings("unchecked")
    public static ItemEntry<Item> getCoin(CoinDenomination denomination) {
        return (ItemEntry<Item>) COINS[denomination.getRank()];
    }

    public static Item getCoinItem(CoinDenomination denomination) {
        return getCoin(denomination).get();
    }
}
