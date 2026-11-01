package com.mo_guang.ctpp.common.item;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.mo_guang.ctpp.common.machine.market.CoinTooltip;

import java.util.List;

import javax.annotation.Nullable;

/**
 * A currency item. Behaviour lives entirely in {@link CoinTooltip}; the class exists only so coins can describe
 * their worth rather than being bare {@code Item}s.
 */
public class CoinItem extends Item {

    public CoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        // Shift is only consulted on the client, and only when a tooltip is actually built, so the client-only
        // Screen reference never loads on a dedicated server.
        CoinTooltip.append(tooltip, CoinTooltip.valueOf(stack), Screen.hasShiftDown());
    }
}
