package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.ctnhlang.CN;
import com.ctnhlang.Category;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.util.List;
import java.util.Locale;

/**
 * Builds coin tooltips.
 * <p>
 * The default line is the stack's worth in its <em>natural</em> unit — blocks from 81 nuggets up, ingots from 9,
 * nuggets below that — coloured to match the coin art (silver for blocks, cupronickel for ingots, copper for
 * nuggets). Holding shift swaps it for an exact breakdown in all three units, to one decimal place.
 */
@Prefix("market")
@Category("machine")
public final class CoinTooltip {

    @CN("%s %s")
    @EN("%s %s")
    private static Lang line;

    @CN("§7按住 Shift 查看全部单位")
    @EN("§7Hold Shift for all units")
    private static Lang hint;

    /**
     * The mark printed for a unit: ○ nugget, ◇ ingot, ⬢ block. These match the specification's notation exactly.
     */
    private static String markFor(CoinDenomination unit) {
        return unit.getMark();
    }

    /*
     * These take a pre-formatted String rather than a double: Component.translatable substitutes with
     * String.format-style "%s" only and never interprets "%.1f", so a double would print all 17 of its
     * significant digits. The caller rounds with trim() before translating.
     */
    @CN("%s 块")
    @EN("%s blocks")
    private static Lang row_block;

    @CN("%s 锭")
    @EN("%s ingots")
    private static Lang row_ingot;

    @CN("%s 粒")
    @EN("%s nuggets")
    private static Lang row_nugget;

    /** Nuggets in one block and in one ingot, for the shift breakdown. */
    private static final long PER_INGOT = 9L;
    private static final long PER_BLOCK = 81L;

    private CoinTooltip() {}

    /**
     * Appends the coin's lines.
     *
     * @param total nugget value of the whole stack
     */
    public static void append(List<Component> tooltip, long total, boolean shift) {
        if (total <= 0) {
            return;
        }
        if (shift) {
            tooltip.add(row_block.translate(trim(total / (double) PER_BLOCK)).withStyle(
                    style -> style.withColor(CoinDenomination.COLOR_BLOCK)));
            tooltip.add(row_ingot.translate(trim(total / (double) PER_INGOT)).withStyle(
                    style -> style.withColor(CoinDenomination.COLOR_INGOT)));
            tooltip.add(row_nugget.translate(trim(total)).withStyle(
                    style -> style.withColor(CoinDenomination.COLOR_NUGGET)));
            return;
        }

        // Whole units only: three silver coins read as "3 ⬢", not "3.0 ⬢".
        CoinDenomination unit = CoinDenomination.unitFor(total);
        long whole = total / unit.getValue();
        tooltip.add(line.translate(Long.toString(whole), markFor(unit))
                .withStyle(style -> style.withColor(unit.getColour())));
        tooltip.add(hint.translate().withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Values the whole stack, including any NBT-distinct copies, in nuggets.
     */
    public static long valueOf(ItemStack stack) {
        CoinDenomination denomination = CoinDenominationRegistry.fromStack(stack);
        return denomination == null ? 0L : denomination.getValue() * stack.getCount();
    }

    /** One decimal place, trailing ".0" kept so the three rows line up. */
    private static String trim(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
