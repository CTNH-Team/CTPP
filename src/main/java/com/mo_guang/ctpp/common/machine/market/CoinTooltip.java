package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import com.ctnhlang.CN;
import com.ctnhlang.Category;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.util.List;

/** Coin tooltips: natural unit by default, all three units to one decimal on shift. */
@Prefix("market")
@Category("machine")
public final class CoinTooltip {

    @CN("%s %s")
    @EN("%s %s")
    private static Lang line;

    @CN("§7按住 Shift 查看全部单位")
    @EN("§7Hold Shift for all units")
    private static Lang hint;

    /** Nugget, ingot or block mark for a unit. */
    private static String markFor(CoinDenomination unit) {
        return unit.getMark();
    }

    // Take %s, not %.1f: Component.translatable never interprets format specifiers.
    @CN("%s 块")
    @EN("%s blocks")
    private static Lang row_block;

    @CN("%s 锭")
    @EN("%s ingots")
    private static Lang row_ingot;

    @CN("%s 粒")
    @EN("%s nuggets")
    private static Lang row_nugget;

    private static final long PER_INGOT = 9L;
    private static final long PER_BLOCK = 81L;

    private CoinTooltip() {}

    /** @param total nugget value of the whole stack */
    public static void append(List<Component> tooltip, long total, boolean shift) {
        if (total <= 0) {
            return;
        }
        if (shift) {
            tooltip.add(row_block.translate(CurrencyUtil.trim(total / (double) PER_BLOCK)).withStyle(
                    style -> style.withColor(CoinDenomination.COLOR_BLOCK)));
            tooltip.add(row_ingot.translate(CurrencyUtil.trim(total / (double) PER_INGOT)).withStyle(
                    style -> style.withColor(CoinDenomination.COLOR_INGOT)));
            tooltip.add(row_nugget.translate(CurrencyUtil.trim(total)).withStyle(
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
}
