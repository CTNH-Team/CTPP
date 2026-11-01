package com.mo_guang.ctpp.common.machine.market;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

/**
 * A uniform view over "goods" — an AE2 {@link AEKey} plus an amount — backed by an item handler or a fluid
 * handler.
 * <p>
 * The market works in AE keys because a ticket line can name either an item or a fluid. This interface hides the
 * two very different Forge APIs behind {@code count}/{@code insert}/{@code extract} so the trade logic stays
 * resource agnostic.
 */
public interface GoodsAccess {

    /** How much of {@code key} this inventory currently holds. */
    long count(AEKey key);

    /**
     * @param simulate when true, report what would happen without changing anything
     * @return how much was actually accepted
     */
    long insert(AEKey key, long amount, boolean simulate);

    /**
     * @param simulate when true, report what would happen without changing anything
     * @return how much was actually removed
     */
    long extract(AEKey key, long amount, boolean simulate);

    /**
     * Items and fluids are stored separately, so a single handler pair backs both. A null handler means that
     * channel is unavailable and every operation on it becomes a no-op.
     */
    record Handlers(@Nullable IItemHandler items, @Nullable IFluidHandler fluids) implements GoodsAccess {

        @Override
        public long count(AEKey key) {
            if (key instanceof AEItemKey itemKey && items != null) {
                long total = 0L;
                for (int slot = 0; slot < items.getSlots(); slot++) {
                    ItemStack stack = items.getStackInSlot(slot);
                    if (AEItemKey.matches(itemKey, stack)) {
                        total += stack.getCount();
                    }
                }
                return total;
            }
            if (key instanceof AEFluidKey fluidKey && fluids != null) {
                Fluid fluid = fluidKey.getFluid();
                long total = 0L;
                for (int tank = 0; tank < fluids.getTanks(); tank++) {
                    FluidStack stack = fluids.getFluidInTank(tank);
                    if (!stack.isEmpty() && stack.getFluid() == fluid) {
                        total += stack.getAmount();
                    }
                }
                return total;
            }
            return 0L;
        }

        @Override
        public long insert(AEKey key, long amount, boolean simulate) {
            if (amount <= 0) {
                return 0L;
            }
            if (key instanceof AEItemKey itemKey && items != null) {
                return insertItem(itemKey, amount, simulate);
            }
            if (key instanceof AEFluidKey fluidKey && fluids != null) {
                FluidStack offer = fluidKey.toStack(clamp(amount));
                if (offer.isEmpty()) {
                    return 0L;
                }
                return fluids.fill(offer,
                        simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            }
            return 0L;
        }

        @Override
        public long extract(AEKey key, long amount, boolean simulate) {
            if (amount <= 0) {
                return 0L;
            }
            if (key instanceof AEItemKey itemKey && items != null) {
                return extractItem(itemKey, amount, simulate);
            }
            if (key instanceof AEFluidKey fluidKey && fluids != null) {
                FluidStack request = fluidKey.toStack(clamp(amount));
                FluidStack drained = fluids.drain(request,
                        simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                return drained.getAmount();
            }
            return 0L;
        }

        private long insertItem(AEItemKey key, long amount, boolean simulate) {
            if (!(items instanceof IItemHandlerModifiable)) {
                return 0L;
            }
            long inserted = 0L;
            long remaining = amount;
            int maxStack = Math.max(1, key.getReadOnlyStack().getMaxStackSize());
            for (int slot = 0; slot < items.getSlots() && remaining > 0; slot++) {
                int chunk = (int) Math.min(remaining, maxStack);
                ItemStack probe = key.toStack(chunk);
                ItemStack leftover = items.insertItem(slot, probe, simulate);
                long moved = chunk - leftover.getCount();
                inserted += moved;
                remaining -= moved;
            }
            return inserted;
        }

        private long extractItem(AEItemKey key, long amount, boolean simulate) {
            if (!(items instanceof IItemHandlerModifiable modifiable)) {
                return 0L;
            }
            long extracted = 0L;
            long remaining = amount;
            for (int slot = 0; slot < items.getSlots() && remaining > 0; slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                if (!AEItemKey.matches(key, stack)) {
                    continue;
                }
                int chunk = (int) Math.min(remaining, stack.getCount());
                ItemStack taken = modifiable.extractItem(slot, chunk, simulate);
                extracted += taken.getCount();
                remaining -= taken.getCount();
            }
            return extracted;
        }

        private static int clamp(long amount) {
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, amount));
        }
    }
}
