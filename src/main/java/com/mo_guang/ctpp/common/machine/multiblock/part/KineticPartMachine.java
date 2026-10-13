package com.mo_guang.ctpp.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import com.mo_guang.ctpp.common.machine.IKineticMachine;
import com.mo_guang.ctpp.common.machine.NotifiableStressTrait;
import com.mo_guang.ctpp.common.machine.multiblock.KineticMultiblockMachine;
import com.mo_guang.ctpp.common.machine.multiblock.KineticOutputMachine;
import lombok.Getter;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class KineticPartMachine extends TieredIOPartMachine implements IKineticMachine {

    private static final int INITIAL_BINDING_GRACE_TICKS = 40;
    private static final String LAST_CONTROLLER_POS_NBT_KEY = "lastControllerPos";
    private static final String BINDING_RELOAD_GRACE_DEADLINE_NBT_KEY = "bindingReloadGraceDeadline";

    @Getter
    protected final NotifiableStressTrait stressTrait;

    @Nullable
    private TickableSubscription bindingCheckSubscription;
    @Persisted
    @Nullable
    private BlockPos lastControllerPos;
    @Persisted
    private long bindingReloadGraceDeadline;
    private boolean bindingInconclusive = true;

    public KineticPartMachine(IMachineBlockEntity holder, int tier, IO io, Object... args) {
        super(holder, tier, io);
        this.stressTrait = attachTrait(createStressTrait(args));
    }

    public IO getIO() {
        return this.io;
    }

    public boolean isValidOutputBinding() {
        if (io != IO.OUT || !getKineticDefinition().isSource()) {
            return true;
        }
        if (!isFormed() || getControllers().isEmpty() ||
                !(getLevel() instanceof ServerLevel serverLevel)) {
            return false;
        }
        var controller = getControllers().first();
        if (!(controller instanceof KineticOutputMachine outputMachine)) {
            return false;
        }
        BlockPos controllerPos = outputMachine.getPos();
        long instanceId = getControllerBindingInstanceId(controllerPos);
        return lastControllerPos != null && lastControllerPos.equals(controllerPos) && instanceId > 0 &&
                MultiblockWorldSavedData.getOrCreate(serverLevel)
                        .isLoadedValidatedControllerMember(controllerPos, instanceId, getPos()) &&
                outputMachine.getStructureInstanceId() == instanceId && outputMachine.isStructureOperational() &&
                outputMachine.isActive() && outputMachine.hasRuntimePart(this);
    }

    //////////////////////////////////////
    // ***** Initialization *****//
    //////////////////////////////////////
    protected NotifiableStressTrait createStressTrait(Object... args) {
        return new NotifiableStressTrait(this, this.io, this.io);
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        if (!isRemote()) {
            if (oldFacing.getAxis() != newFacing.getAxis()) {
                var holder = getKineticHolder();
                if (holder.hasNetwork()) {
                    holder.getOrCreateNetwork().remove(holder);
                }
                holder.detachKinetics();
                holder.removeSource();
            }
        }
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        lastControllerPos = null;
        bindingInconclusive = false;
        clearBindingReloadGrace();
        // A part unloading from its chunk is not a structure failure. Its holder is already invalid in that path, so
        // retain the persisted output; a definitive controller invalidation still stops every loaded output part.
        if (!isInValid()) {
            stressTrait.stopWorking();
        }
    }

    @Override
    public void unloadedFromController(IMultiController controller) {
        // Preserve the applied Create source while either side of the multiblock is temporarily unloaded. Calling the
        // superclass unload implementation clears only the runtime controller association; lastControllerPos remains
        // as the non-loading proof needed to distinguish an unloaded controller from a removed one.
        super.unloadedFromController(controller);
        bindingInconclusive = true;
        startBindingReloadGrace();
    }

    @Override
    public void onControllerBindingRetired(BlockPos controllerPos, long instanceId) {
        if (lastControllerPos != null && lastControllerPos.equals(controllerPos)) {
            lastControllerPos = null;
            bindingInconclusive = false;
            clearBindingReloadGrace();
            if (!isInValid()) {
                stressTrait.stopWorking();
            }
        }
    }

    void checkOutputBinding() {
        if (io != IO.OUT || !getKineticDefinition().isSource()) {
            return;
        }
        if (isValidOutputBinding()) {
            bindingInconclusive = false;
            clearBindingReloadGrace();
            getKineticHolder().setGeneratedSourceSuspended(false);
            return;
        }

        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        var savedData = MultiblockWorldSavedData.getOrCreate(serverLevel);
        long instanceId = lastControllerPos == null ? 0 : getControllerBindingInstanceId(lastControllerPos);
        if (lastControllerPos != null && instanceId > 0 &&
                savedData.getControllerBindingStatus(lastControllerPos, instanceId) ==
                        MultiblockWorldSavedData.ControllerBindingStatus.RETIRED) {
            // A tombstone is conclusive even while the owner chunk is unavailable. Retire before granting reload
            // grace; the callback queues source withdrawal after Create restores its saved contribution.
            reconcileControllerBindings(savedData);
            return;
        }
        IMultiController exactController = lastControllerPos == null || instanceId <= 0 ? null :
                savedData.getLoadedControllerInstance(lastControllerPos, instanceId);
        boolean controllerPositionTicking = lastControllerPos != null &&
                savedData.isControllerPositionLoadedNoChunkRequest(lastControllerPos);
        boolean ownerUnavailable = lastControllerPos != null && instanceId > 0 &&
                exactController == null && !controllerPositionTicking;
        if (ownerUnavailable && !bindingInconclusive) {
            // Runtime ownership was just lost because its chunk became unobservable. Give the same bounded window as a
            // disk reload before withdrawing only the applied source.
            bindingInconclusive = true;
            startBindingReloadGrace();
        } else if (!ownerUnavailable) {
            // A loaded exact controller (including one explicitly pending revalidation), or an entity-ticking owner
            // position where the exact epoch is absent, is conclusive enough to fail closed immediately. The grace is
            // only for an owner chunk whose state cannot currently be observed.
            bindingInconclusive = false;
            clearBindingReloadGrace();
        }
        if (ownerUnavailable && serverLevel.getGameTime() < bindingReloadGraceDeadline) {
            return;
        }

        // Past the bounded reload window, anything short of an exact ACTIVE epoch, operational controller, validated
        // world mapping, runtime membership, and active output fails closed. Ownership and the requested speed remain
        // persisted; only the Create source contribution is withdrawn and can be restored on the next exact match.
        getKineticHolder().setGeneratedSourceSuspended(true);
        if (lastControllerPos == null || instanceId <= 0) {
            // An output without an exact persisted owner cannot resume. This also covers a save between owner
            // invalidation and the holder's end-of-tick source reconciliation.
            lastControllerPos = null;
            bindingInconclusive = false;
            getKineticHolder().setChanged();
            stressTrait.stopWorking();
            return;
        }
        if (getOffsetTimer() % 20 != 0) {
            return;
        }

        if (exactController == null) {
            if (savedData.isControllerPositionLoadedNoChunkRequest(lastControllerPos)) {
                // An entity-ticking position with no exact epoch is positive deletion/replacement evidence. Let the
                // base owner protocol retire the binding and invoke onControllerBindingRetired().
                reconcileControllerBindings(savedData);
            }
            // Missing/FULL-but-not-entity-ticking chunks remain inconclusive. Preserve ownership and desired speed;
            // the fail-closed transition above has withdrawn only the applied source.
            return;
        }

        if (exactController.isStructureRevalidationPending()) {
            return;
        }

        if (!(exactController instanceof KineticOutputMachine outputMachine)) {
            removedFromController(exactController);
            return;
        }
        if (!outputMachine.isStructureOperational() || !outputMachine.hasRuntimePart(this)) {
            // The exact controller is fully available and no longer owns this part. This is definitive, not a timeout.
            reconcileControllerBindings(savedData);
            return;
        }

        if (savedData.mapping.get(lastControllerPos) != outputMachine.getMultiblockState()) {
            // A direct/addon formation path can briefly expose runtime membership before the world reverse index is
            // installed. Retain the exact owner, but never publish power until the validated mapping is visible.
            return;
        }

        bindingInconclusive = false;
        if (outputMachine.isActive()) {
            getKineticHolder().setGeneratedSourceSuspended(false);
        }
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        if (io == IO.OUT && getKineticDefinition().isSource()) {
            lastControllerPos = controller.self().getPos().immutable();
            bindingInconclusive = false;
            clearBindingReloadGrace();
            getKineticHolder().setGeneratedSourceSuspended(!isValidOutputBinding());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        bindingInconclusive = true;
        if (getLevel() instanceof ServerLevel serverLevel &&
                lastControllerPos != null && getControllerBindingInstanceId(lastControllerPos) > 0 &&
                !getKineticHolder().isGeneratedSourceSuspended() &&
                getKineticHolder().getAppliedGeneratedSpeed() != 0.0F) {
            long latestSafeDeadline = serverLevel.getGameTime() + INITIAL_BINDING_GRACE_TICKS;
            if (bindingReloadGraceDeadline <= 0 || bindingReloadGraceDeadline > latestSafeDeadline) {
                bindingReloadGraceDeadline = latestSafeDeadline;
                getKineticHolder().setChanged();
            }
        }
        if (bindingCheckSubscription == null) {
            bindingCheckSubscription = subscribeServerTick(this::checkOutputBinding);
        }
    }

    private void startBindingReloadGrace() {
        if (bindingReloadGraceDeadline == 0 && getLevel() instanceof ServerLevel serverLevel) {
            bindingReloadGraceDeadline = serverLevel.getGameTime() + INITIAL_BINDING_GRACE_TICKS;
            getKineticHolder().setChanged();
        }
    }

    private void clearBindingReloadGrace() {
        if (bindingReloadGraceDeadline != 0) {
            bindingReloadGraceDeadline = 0;
            getKineticHolder().setChanged();
        }
    }

    @Override
    public void onUnload() {
        if (bindingCheckSubscription != null) {
            bindingCheckSubscription.unsubscribe();
        }
        bindingCheckSubscription = null;
        super.onUnload();
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop) {
            // Item NBT must not carry a controller claim or a reload grace into a new placement.
            tag.remove(LAST_CONTROLLER_POS_NBT_KEY);
            tag.remove(BINDING_RELOAD_GRACE_DEADLINE_NBT_KEY);
        }
    }

    @Override
    public void onChanged() {
        super.onChanged();
        if (!getControllers().isEmpty() &&
                getControllers().first() instanceof KineticMultiblockMachine kineticMultiblockMachine) {
            kineticMultiblockMachine.onChanged();
        }
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        if (!workingEnabled) {
            stressTrait.stopWorking();
        }
        super.setWorkingEnabled(workingEnabled);
    }

    //////////////////////////////////////
    // ********* GUI *********//
    //////////////////////////////////////
    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    @Override
    public boolean canShared() {
        return false;
    }
}
