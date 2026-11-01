package com.mo_guang.ctpp.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.ctnhlang.CN;
import com.ctnhlang.Category;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import com.mo_guang.ctpp.common.machine.market.MarketTicket;
import com.mo_guang.ctpp.common.machine.market.TradeEntry;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.util.List;

import javax.annotation.Nullable;

/**
 * The market ticket: a paper slip carrying one or more trade lines in NBT.
 * <p>
 * The item itself is deliberately inert. All behaviour lives in the trading machine, which reads the NBT through
 * {@link MarketTicket}; this class only exists so the slip can be described to the player.
 */
@Prefix("market")
@Category("machine")
public class MarketTicketItem extends Item {

    @CN("空白单据")
    @EN("Blank Ticket")
    private static Lang ticket_blank;

    @CN("该单据的额度已用尽")
    @EN("This ticket has no trades left")
    private static Lang ticket_exhausted;

    @CN("%s · %s")
    @EN("%s · %s")
    private static Lang ticket_named;

    public MarketTicketItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        List<TradeEntry> entries = MarketTicket.getEntries(stack);
        if (entries.isEmpty()) {
            tooltip.add(ticket_blank.translate().withStyle(ChatFormatting.GRAY));
            return;
        }
        for (TradeEntry entry : entries) {
            // The entry's own describe() already carries goods, price, direction and quota.
            tooltip.add(entry.describe().withStyle(ChatFormatting.GRAY));
        }

        if (MarketTicket.isExhausted(stack)) {
            tooltip.add(ticket_exhausted.translate().withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // A live ticket glints so a spent one is easy to tell apart in a chest.
        return !MarketTicket.isExhausted(stack) && !MarketTicket.getEntries(stack).isEmpty();
    }

    @Override
    public Component getName(ItemStack stack) {
        List<TradeEntry> entries = MarketTicket.getEntries(stack);
        if (entries.isEmpty()) {
            return super.getName(stack);
        }
        // Show the headline trade in the item name: "Market Ticket · <goods>".
        return ticket_named.translate(super.getName(stack), entries.get(0).content().getDisplayName());
    }
}
