package com.mo_guang.ctpp.common.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** A persistent identity for one validated multiblock-controller ownership epoch. */
public record MultiblockOwner(BlockPos controllerPos, long instanceId) implements Comparable<MultiblockOwner> {

    private static final String CONTROLLER_POS = "ControllerPos";
    private static final String INSTANCE_ID = "InstanceId";

    public MultiblockOwner {
        controllerPos = controllerPos.immutable();
    }

    public boolean isValid() {
        return instanceId > 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(CONTROLLER_POS, controllerPos.asLong());
        tag.putLong(INSTANCE_ID, instanceId);
        return tag;
    }

    public static MultiblockOwner load(CompoundTag tag) {
        if (!tag.contains(CONTROLLER_POS, Tag.TAG_LONG)) {
            return new MultiblockOwner(BlockPos.ZERO, 0);
        }
        return new MultiblockOwner(BlockPos.of(tag.getLong(CONTROLLER_POS)), tag.getLong(INSTANCE_ID));
    }

    @Override
    public int compareTo(MultiblockOwner other) {
        int positionComparison = Long.compare(controllerPos.asLong(), other.controllerPos.asLong());
        return positionComparison != 0 ? positionComparison : Long.compare(instanceId, other.instanceId);
    }
}
