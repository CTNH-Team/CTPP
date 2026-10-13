package com.mo_guang.ctpp.common.blockentity;

import net.minecraft.core.BlockPos;

public interface IKineticBlockEntityExtension {

    void ctpp$claimMultiblockOwner(BlockPos controllerPos, long instanceId, float visualSpeed);

    void ctpp$releaseMultiblockOwner(BlockPos controllerPos, long instanceId);
}
