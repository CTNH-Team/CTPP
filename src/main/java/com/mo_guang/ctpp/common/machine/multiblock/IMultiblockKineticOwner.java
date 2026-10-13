package com.mo_guang.ctpp.common.machine.multiblock;

import net.minecraft.core.BlockPos;

/** Loaded-controller membership used to retire persisted Create-side claims without loading owner chunks. */
public interface IMultiblockKineticOwner {

    boolean ownsKineticVisual(BlockPos pos);

    /** Current per-owner visual request used by loaded-member reconciliation. */
    float getKineticVisualSpeed(BlockPos pos);
}
