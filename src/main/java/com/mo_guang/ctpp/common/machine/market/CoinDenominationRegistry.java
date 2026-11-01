package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reverse lookup from a registered coin item back to its {@link CoinDenomination}.
 * <p>
 * The forward direction lives on the enum ({@link CoinDenomination#getItem()}); this class only exists so that
 * "is this stack money, and which rank?" does not need a linear scan over the enum on every slot read. The map is
 * built lazily because registrate may not have run when the class is first loaded.
 */
public final class CoinDenominationRegistry {

    private static Map<Item, CoinDenomination> byItem;

    private CoinDenominationRegistry() {}

    private static Map<Item, CoinDenomination> byItem() {
        Map<Item, CoinDenomination> map = byItem;
        if (map == null) {
            map = new IdentityHashMap<>();
            for (CoinDenomination denomination : CoinDenomination.values()) {
                Item item = denomination.getItem();
                if (item != null) {
                    map.put(item, denomination);
                }
            }
            byItem = map;
        }
        return map;
    }

    @Nullable
    public static CoinDenomination fromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return fromItem(stack.getItem());
    }

    @Nullable
    public static CoinDenomination fromItem(@Nullable Item item) {
        if (item == null) {
            return null;
        }
        return byItem().get(item);
    }

    public static boolean isCoin(ItemStack stack) {
        return fromStack(stack) != null;
    }

    /** Drops the lazily built index; only needed if coin items are ever re-registered. */
    public static void invalidate() {
        byItem = null;
    }
}
