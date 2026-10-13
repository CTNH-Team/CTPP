package com.mo_guang.ctpp.mixin.create;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import com.mo_guang.ctpp.common.blockentity.IKineticBlockEntityExtension;
import com.mo_guang.ctpp.common.blockentity.KineticMachineBlockEntity;
import com.mo_guang.ctpp.common.blockentity.MultiblockOwner;
import com.mo_guang.ctpp.common.machine.multiblock.IMultiblockKineticOwner;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.TreeMap;

@Mixin(KineticBlockEntity.class)
public class KineticBlockEntityMixin implements IKineticBlockEntityExtension {

    @Unique
    private static final String CTNH$OWNERS = "CTPPMultiblockOwners";
    @Unique
    private static final String CTNH$VISUAL_SPEED = "VisualSpeed";
    @Unique
    private static final int CTNH$RECONCILE_INTERVAL = 100;

    @Unique
    private final Map<MultiblockOwner, Float> CTNH$multiblockOwners = new TreeMap<>();

    @Unique
    private int CTNH$ownerReconcileTicks;

    @Unique
    private boolean CTNH$persistSafeOwnersAfterDiskLoad;

    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void ctpp$writeMultiblockMarker(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        if (!CTNH$multiblockOwners.isEmpty()) {
            ListTag owners = new ListTag();
            for (var entry : CTNH$multiblockOwners.entrySet()) {
                CompoundTag owner = entry.getKey().save();
                owner.putFloat(CTNH$VISUAL_SPEED, entry.getValue());
                owners.add(owner);
            }
            compound.put(CTNH$OWNERS, owners);
        }
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void ctpp$readMultiblockMarker(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        CTNH$multiblockOwners.clear();
        ListTag owners = compound.getList(CTNH$OWNERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++) {
            CompoundTag ownerTag = owners.getCompound(i);
            MultiblockOwner owner = MultiblockOwner.load(ownerTag);
            if (owner.isValid()) {
                float visualSpeed = ownerTag.getFloat(CTNH$VISUAL_SPEED);
                visualSpeed = Float.isFinite(visualSpeed) ? visualSpeed : 0.0F;
                // Disk requests are provisional until their controller is loaded and operational again.
                CTNH$multiblockOwners.put(owner, clientPacket ? visualSpeed : 0.0F);
            }
        }
        // Stable position phases spread a large structure's owner checks across the full interval after bulk loading.
        CTNH$ownerReconcileTicks = ctpp$initialReconcileDelay(((KineticBlockEntity) (Object) this).getBlockPos());
        CTNH$persistSafeOwnersAfterDiskLoad = !clientPacket && !CTNH$multiblockOwners.isEmpty();
    }

    @Inject(method = "getSpeed", at = @At("RETURN"), remap = false, cancellable = true)
    private void ctpp$provideVisualSpeed(CallbackInfoReturnable<Float> cir) {
        KineticBlockEntity blockEntity = (KineticBlockEntity) (Object) this;
        if (blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide && ctpp$isClaimed() &&
                !(blockEntity instanceof KineticMachineBlockEntity)) {
            cir.setReturnValue(ctpp$getAggregatedVisualSpeed());
        }
    }

    @Inject(method = "needsSpeedUpdate", at = @At("RETURN"), remap = false, cancellable = true)
    private void ctpp$disableKineticReattachment(CallbackInfoReturnable<Boolean> cir) {
        KineticBlockEntity blockEntity = (KineticBlockEntity) (Object) this;
        if (ctpp$isClaimed() && !(blockEntity instanceof KineticMachineBlockEntity)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "initialize", at = @At("TAIL"), remap = false)
    private void ctpp$detachRestoredOwnedMemberAfterNativeAccounting(CallbackInfo ci) {
        KineticBlockEntity blockEntity = (KineticBlockEntity) (Object) this;
        if (ctpp$isClaimed() && !(blockEntity instanceof KineticMachineBlockEntity) &&
                blockEntity.getLevel() instanceof ServerLevel) {
            // Create must first run initFromTE/addSilently so its unloaded network aggregate is consumed exactly once.
            // The render-only multiblock member can then leave that live network before the first server tick.
            ctpp$detachFromCreateNetwork(blockEntity);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void ctpp$reconcileMultiblockOwners(CallbackInfo ci) {
        KineticBlockEntity blockEntity = (KineticBlockEntity) (Object) this;
        if (CTNH$persistSafeOwnersAfterDiskLoad && blockEntity.getLevel() instanceof ServerLevel) {
            CTNH$persistSafeOwnersAfterDiskLoad = false;
            blockEntity.setChanged();
        }
        if (!(blockEntity.getLevel() instanceof ServerLevel serverLevel) ||
                CTNH$multiblockOwners.isEmpty()) {
            return;
        }
        if (--CTNH$ownerReconcileTicks > 0) {
            return;
        }
        CTNH$ownerReconcileTicks = CTNH$RECONCILE_INTERVAL;

        boolean wasClaimed = ctpp$isClaimed();
        boolean changed = false;
        var savedData = MultiblockWorldSavedData.getOrCreate(serverLevel);
        var iterator = CTNH$multiblockOwners.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            MultiblockOwner owner = entry.getKey();
            var status = savedData.getControllerBindingStatus(owner.controllerPos(), owner.instanceId());
            if (status == MultiblockWorldSavedData.ControllerBindingStatus.RETIRED) {
                iterator.remove();
                changed = true;
                continue;
            }
            IMultiController controller = savedData.getLoadedControllerInstance(owner.controllerPos(),
                    owner.instanceId());
            if (controller == null) {
                // A missing owner chunk keeps the exact claim but cannot keep publishing its last transient output.
                // A loaded+ticking position with no exact ownership epoch is positive stale-owner evidence.
                if (savedData.isControllerPositionLoadedNoChunkRequest(owner.controllerPos())) {
                    iterator.remove();
                    changed = true;
                } else if (Float.compare(entry.getValue(), 0.0F) != 0) {
                    entry.setValue(0.0F);
                    changed = true;
                }
                continue;
            }
            if (controller.isStructureRevalidationPending()) {
                if (Float.compare(entry.getValue(), 0.0F) != 0) {
                    entry.setValue(0.0F);
                    changed = true;
                }
                continue;
            }
            if (!controller.isStructureOperational() ||
                    !(controller instanceof IMultiblockKineticOwner kineticOwner) ||
                    !kineticOwner.ownsKineticVisual(blockEntity.getBlockPos())) {
                iterator.remove();
                changed = true;
                continue;
            }
            float requested = kineticOwner.getKineticVisualSpeed(blockEntity.getBlockPos());
            requested = Float.isFinite(requested) ? requested : 0.0F;
            if (Float.compare(entry.getValue(), requested) != 0) {
                entry.setValue(requested);
                changed = true;
            }
        }
        if (changed) {
            ctpp$finishOwnershipChange(blockEntity, wasClaimed);
        }
    }

    @Override
    public void ctpp$claimMultiblockOwner(BlockPos controllerPos, long instanceId, float visualSpeed) {
        if (instanceId <= 0) {
            return;
        }
        KineticBlockEntity blockEntity = (KineticBlockEntity) (Object) this;
        boolean hadExactOwner = !CTNH$multiblockOwners.isEmpty();
        if (!hadExactOwner) {
            CTNH$ownerReconcileTicks = ctpp$initialReconcileDelay(blockEntity.getBlockPos());
        }
        MultiblockOwner owner = new MultiblockOwner(controllerPos, instanceId);
        float requested = Float.isFinite(visualSpeed) ? visualSpeed : 0.0F;
        Float previous = CTNH$multiblockOwners.put(owner, requested);
        if (previous != null && Float.compare(previous, requested) == 0) {
            return;
        }
        ctpp$finishOwnershipChange(blockEntity, hadExactOwner);
    }

    @Override
    public void ctpp$releaseMultiblockOwner(BlockPos controllerPos, long instanceId) {
        if (instanceId <= 0) {
            return;
        }
        KineticBlockEntity blockEntity = (KineticBlockEntity) (Object) this;
        boolean wasClaimed = ctpp$isClaimed();
        if (CTNH$multiblockOwners.remove(new MultiblockOwner(controllerPos, instanceId)) != null) {
            ctpp$finishOwnershipChange(blockEntity, wasClaimed);
        }
    }

    @Unique
    private boolean ctpp$isClaimed() {
        return !CTNH$multiblockOwners.isEmpty();
    }

    @Unique
    private float ctpp$getAggregatedVisualSpeed() {
        float selected = 0.0F;
        float selectedMagnitude = 0.0F;
        for (float candidate : CTNH$multiblockOwners.values()) {
            float magnitude = Math.abs(candidate);
            if (magnitude > selectedMagnitude) {
                selected = candidate;
                selectedMagnitude = magnitude;
            }
        }
        return selected;
    }

    @Unique
    private void ctpp$finishOwnershipChange(KineticBlockEntity blockEntity, boolean wasClaimed) {
        boolean claimed = ctpp$isClaimed();
        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide) {
            if (!wasClaimed && claimed && !(blockEntity instanceof KineticMachineBlockEntity)) {
                ctpp$detachFromCreateNetwork(blockEntity);
            } else if (wasClaimed && !claimed) {
                // Allow ordinary Create propagation again only after the final exact owner is gone.
                blockEntity.updateSpeed = true;
            }
            blockEntity.setChanged();
            blockEntity.sendData();
        }
    }

    @Unique
    private void ctpp$detachFromCreateNetwork(KineticBlockEntity blockEntity) {
        // A render-only member must leave its physical network when the first controller claims it.
        if (blockEntity.hasNetwork() || blockEntity.hasSource() || blockEntity.getTheoreticalSpeed() != 0) {
            blockEntity.detachKinetics();
            blockEntity.setSpeed(0);
            blockEntity.source = null;
            blockEntity.sequenceContext = null;
            blockEntity.setNetwork(null);
        }
        blockEntity.updateSpeed = false;
    }

    @Unique
    private static int ctpp$initialReconcileDelay(BlockPos pos) {
        return Math.floorMod(Long.hashCode(pos.asLong()), CTNH$RECONCILE_INTERVAL) + 1;
    }
}
