package com.mo_guang.ctpp.client;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.function.Predicate;

/**
 * A slot that only accepts shift-clicked stacks matching its predicate. LowDragLib sorts shift-click destinations
 * by slot index, so each region must refuse foreign items for the transfer to reach the right one.
 */
public class MarketSlotWidget extends SlotWidget {

    private final Predicate<ItemStack> shiftClickFilter;

    /** @param shiftClickFilter what this slot accepts from a shift-click; ordinary clicking is unaffected */
    public MarketSlotWidget(IItemHandlerModifiable handler, int slotIndex, int x, int y,
                            Predicate<ItemStack> shiftClickFilter) {
        super(handler, slotIndex, x, y, true, true);
        this.shiftClickFilter = shiftClickFilter;
    }

    @Override
    public boolean canMergeSlot(ItemStack stack) {
        return shiftClickFilter.test(stack) && super.canMergeSlot(stack);
    }
}
