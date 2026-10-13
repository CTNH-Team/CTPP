package com.mo_guang.ctpp.common.blockentity;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.registry.GTRegistration;
import com.gregtechceu.gtceu.utils.ManagedFieldHolderMap;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.syncdata.managed.MultiManagedStorage;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.mo_guang.ctpp.api.IBlockStressValues;
import com.mo_guang.ctpp.api.KineticMachineDefinition;
import com.mo_guang.ctpp.client.CarbonBrushesRenderer;
import com.mo_guang.ctpp.client.CarbonBrushesVisual;
import com.mo_guang.ctpp.client.KineticMachineBlockEntityRenderer;
import com.mo_guang.ctpp.common.machine.IKineticMachine;
import com.mo_guang.ctpp.common.machine.multiblock.part.KineticPartMachine;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticEffectHandler;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.tterrag.registrate.util.OneTimeEventReceiver;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class KineticMachineBlockEntity extends KineticBlockEntity implements IMachineBlockEntity, IManaged {

    static {
        ManagedFieldHolderMap.createManagedFieldHolder(KineticMachineBlockEntity.class);
    }

    public final MultiManagedStorage managedStorage = new MultiManagedStorage();

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    public final MetaMachine metaMachine;

    private static final String APPLIED_SPEED_NBT_KEY = "appliedGeneratedSpeed";
    private static final String WORKING_SPEED_NBT_KEY = "workingSpeed";
    private static final String SOURCE_SUSPENDED_NBT_KEY = "generatedSourceSuspended";

    private final long offset = GTValues.RNG.nextInt(20);

    /**
     * The speed requested by the GT recipe/multiblock logic.
     */
    @Persisted
    @DescSynced
    public float workingSpeed;

    /**
     * The speed that is currently registered as this block entity's Create network source. Unlike
     * {@link #workingSpeed}, this value must not depend on the transient GT multiblock binding during world or chunk
     * load.
     */
    @Getter
    @Persisted
    @DescSynced
    private float appliedGeneratedSpeed;

    /**
     * Keeps a persisted output request and ownership binding while withdrawing only its live Create source. A newly
     * loaded source retains its applied speed long enough for {@link KineticBlockEntity#initialize()} to consume its
     * unloaded-network contribution exactly once; the owning part then suspends it if its controller remains
     * unavailable after the bounded reload grace period.
     */
    @Persisted
    private boolean generatedSourceSuspended;

    /** Requests one server-thread reconciliation after Create has initialized its persisted network state. */
    public boolean reActivateSource;

    /**
     * Guards the mandatory first-tick reconciliation from Create's propagation callbacks. In particular,
     * {@link #setSource(BlockPos)} may clear {@link #reActivateSource} while {@code super.tick()} restores a saved
     * network, but it must not cancel stale-network cleanup for this newly loaded block entity.
     */
    private boolean sourceReconcilePending;
    /** Keeps corrupt/out-of-range saved source state out of propagation until post-initialize repair. */
    private boolean preAttachReconciliationPending;
    /** Marks a non-finite persisted source identity that must not enter KineticNetwork.addSilently(). */
    private boolean corruptKineticStatePending;

    protected KineticMachineBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.metaMachine = getDefinition().createMetaMachine(this);

        this.getRootStorage().attach(getSyncStorage());
    }

    public static KineticMachineBlockEntity create(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        return new KineticMachineBlockEntity(typeIn, pos, state);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        var result = getMetaMachine().getCapability(cap, side);
        return result.isPresent() ? result : super.getCapability(cap, side);
    }

    public static void onBlockEntityRegister(BlockEntityType<?> blockEntityType) {
        if (LDLib.isClient()) {
            var type = (BlockEntityType<KineticMachineBlockEntity>) blockEntityType;

            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> OneTimeEventReceiver.addModListener(
                            GTRegistration.REGISTRATE,
                            FMLClientSetupEvent.class,
                            ($) -> {
                                SimpleBlockEntityVisualizer.builder(type)
                                        .factory(SingleAxisRotatingVisual::shaft)
                                        .skipVanillaRender((be) -> false)
                                        .apply();

                                BlockEntityRenderers.register(type, KineticMachineBlockEntityRenderer::new);
                            }));
        }
    }

    public static void onCarbonBrushesBlockEntityRegister(BlockEntityType<?> blockEntityType) {
        if (LDLib.isClient()) {
            var type = (BlockEntityType<KineticMachineBlockEntity>) blockEntityType;

            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> OneTimeEventReceiver.addModListener(
                            GTRegistration.REGISTRATE,
                            FMLClientSetupEvent.class,
                            ($) -> {
                                SimpleBlockEntityVisualizer.builder(type)
                                        .factory(CarbonBrushesVisual::new)
                                        .skipVanillaRender((be) -> false)
                                        .apply();

                                BlockEntityRenderers.register(type, CarbonBrushesRenderer::new);
                            }));
        }
    }

    @Override
    public KineticMachineDefinition getDefinition() {
        return (KineticMachineDefinition) IMachineBlockEntity.super.getDefinition();
    }

    @Override
    public KineticMachineBlockEntity self() {
        return this;
    }

    @Override
    public boolean triggerEvent(int id, int para) {
        if (id == 1) { // chunk re render
            if (level != null && level.isClientSide) {
                scheduleRenderUpdate();
            }
            return true;
        }
        return false;
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        if (metaMachine instanceof KineticPartMachine kineticPartMachine) {
            kineticPartMachine.onChanged();
        }
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        metaMachine.onLoad();
        if (!level.isClientSide) {
            float maxRotationSpeed = AllConfigs.server().kinetics.maxRotationSpeed.get();
            boolean corruptNetworkState = corruptKineticStatePending || !Float.isFinite(this.speed) ||
                    !Float.isFinite(capacity) || !Float.isFinite(stress) || !Float.isFinite(lastCapacityProvided) ||
                    !Float.isFinite(lastStressApplied);
            boolean restoredSpeedOutOfRange = Math.abs(this.speed) > maxRotationSpeed ||
                    Math.abs(appliedGeneratedSpeed) > maxRotationSpeed;
            if (getDefinition().isSource() && (corruptNetworkState || restoredSpeedOutOfRange)) {
                // Invalid saved source state must not enter propagation or KineticNetwork's unloaded aggregate.
                // Discard only this block entity's membership, then rebuild it from the clamped desired speed after
                // initialize. This also prevents a neighbour that ticks first from seeing an over-speed source.
                clearKineticInformation();
                appliedGeneratedSpeed = 0.0F;
                preAttachReconciliationPending = true;
                reActivateSource = true;
                sourceReconcilePending = true;
            }
            corruptKineticStatePending = false;
            if (getDefinition().isSource() &&
                    (hasNetwork() || hasSource() || getTheoreticalSpeed() != 0.0F || workingSpeed != 0.0F ||
                            appliedGeneratedSpeed != 0.0F)) {
                // tick() reconciles only after KineticBlockEntity.initialize() has restored this member and the
                // aggregate contribution of still-unloaded members from NBT.
                reActivateSource = true;
                sourceReconcilePending = true;
            }
        }
    }

    @Override
    public MultiManagedStorage getRootStorage() {
        return managedStorage;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        metaMachine.onUnload();
    }

    // @Override
    // public void clearRemoved() {
    // super.clearRemoved();
    // metaMachine.onLoad();
    // }

    @Override
    public boolean shouldRenderGrid(Player player, BlockPos pos, BlockState state, ItemStack held,
                                    Set<GTToolType> toolTypes) {
        return metaMachine.shouldRenderGrid(player, pos, state, held, toolTypes);
    }

    @Override
    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes,
                                    Direction side) {
        return metaMachine.sideTips(player, pos, state, toolTypes, side);
    }

    //////////////////////////////////////
    // ********* Create *********//
    //////////////////////////////////////

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap) {
        return super.getCapability(cap);
    }

    public KineticEffectHandler getEffects() {
        return effects;
    }

    public float scheduleWorking(float su, boolean simulate) {
        if (getDefinition().isSource() && isValidOutputSource()) {
            float speed = Math.min(AllConfigs.server().kinetics.maxRotationSpeed.get(),
                    su / getDefinition().getTorque());
            if (!simulate && Float.compare(workingSpeed, speed) != 0) {
                workingSpeed = speed;
                reActivateSource = true;
                sourceReconcilePending = true;
                setChanged();
            }
            return speed * getDefinition().getTorque();
        }
        return 0;
    }

    public void scheduleWorking(float su) {
        scheduleWorking(su, false);
    }

    public void stopWorking() {
        if (getDefinition().isSource() &&
                (workingSpeed != 0 || appliedGeneratedSpeed != 0 || generatedSourceSuspended)) {
            workingSpeed = 0;
            generatedSourceSuspended = false;
            reActivateSource = true;
            sourceReconcilePending = true;
            setChanged();
        }
    }

    /**
     * Pause or resume the applied Create source without discarding the recipe's desired speed. This transition is
     * reconciled after the current MetaMachine tick, preserving Create's initialize/addSilently accounting order.
     */
    public void setGeneratedSourceSuspended(boolean suspended) {
        if (!getDefinition().isSource() || generatedSourceSuspended == suspended) {
            return;
        }
        generatedSourceSuspended = suspended;
        reActivateSource = true;
        sourceReconcilePending = true;
        setChanged();
    }

    public boolean isGeneratedSourceSuspended() {
        return generatedSourceSuspended;
    }

    @Override
    public float getGeneratedSpeed() {
        return appliedGeneratedSpeed;
    }

    private boolean isValidOutputSource() {
        return !(metaMachine instanceof KineticPartMachine) || isKineticPartFormed();
    }

    private boolean isKineticPartFormed() {
        return metaMachine instanceof KineticPartMachine kineticPartMachine &&
                kineticPartMachine.isValidOutputBinding();
    }

    public void removeSource() {
        if (getDefinition().isSource() && this.hasSource() && this.isSource()) {
            this.reActivateSource = true;
        }
        super.removeSource();
    }

    public void setSource(BlockPos source) {
        super.setSource(source);
        if (!getDefinition().isSource()) return;
        BlockEntity tileEntity = this.level.getBlockEntity(source);
        if (tileEntity instanceof KineticBlockEntity sourceTe) {
            if (this.reActivateSource && Math.abs(sourceTe.getSpeed()) >= Math.abs(this.getGeneratedSpeed())) {
                this.reActivateSource = false;
            }
        }
    }

    public void tick() {
        if (!level.isClientSide && (preAttachReconciliationPending || sourceReconcilePending)) {
            // A finite old source contribution must reach initialize()/addSilently() before it is replaced; corrupt
            // membership was scrubbed in onLoad(). A normal restored source is also held until Create has accounted
            // its persisted membership through initialize()/addSilently().
            updateSpeed = false;
        }
        super.tick();
        if (level.isClientSide) {
            return;
        }
        if (getDefinition().isSource() && (reActivateSource || sourceReconcilePending)) {
            // MetaMachine ticks before its holder, so all requests made during the current GT tick are coalesced into
            // one topology update after Create's first-tick network restoration. A stable restored source stays in its
            // existing network so KineticNetwork's aggregate state for still-unloaded members is preserved.
            reActivateSource = false;
            sourceReconcilePending = false;
            boolean forceTopologyRebuild = preAttachReconciliationPending;
            preAttachReconciliationPending = false;
            updateGeneratedRotation(forceTopologyRebuild);
        }
    }

    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (getMetaMachine() instanceof IKineticMachine kineticMachine &&
                kineticMachine.addToGoggleTooltip(tooltip, isPlayerSneaking)) {
            return true;
        }

        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        float stressBase = this.calculateAddedStressCapacity();
        if (stressBase != 0.0F && IRotate.StressImpact.isEnabled()) {
            CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);
            CreateLang.translate("tooltip.stressImpact").style(ChatFormatting.GRAY).forGoggles(tooltip);
            float speed = this.getTheoreticalSpeed();
            if (speed != this.getGeneratedSpeed() && speed != 0.0F) {
                stressBase *= this.getGeneratedSpeed() / speed;
            }

            speed = Math.abs(speed);
            float stressTotal = stressBase * speed;
            CreateLang.number(stressTotal).translate("generic.unit.stress").style(ChatFormatting.AQUA).space()
                    .add(CreateLang.translate("gui.goggles.at_current_speed").style(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 1);
            added = true;
        }

        return added;
    }

    /**
     * 只允许服务器侧更新转速与应力网络：客户端由 @DescSynced 同步显示，不做本地写。
     */
    public void updateGeneratedRotation() {
        updateGeneratedRotation(false);
    }

    private void updateGeneratedRotation(boolean forceTopologyRebuild) {
        if (!getDefinition().isSource() || level == null || level.isClientSide) {
            return;
        }
        float maxRotationSpeed = AllConfigs.server().kinetics.maxRotationSpeed.get();
        float desiredSpeed = Float.isFinite(workingSpeed) ?
                Math.max(-maxRotationSpeed, Math.min(maxRotationSpeed, workingSpeed)) : 0.0F;
        if (desiredSpeed != workingSpeed) {
            workingSpeed = desiredSpeed;
        }
        float targetSpeed = generatedSourceSuspended ? 0.0F : desiredSpeed;

        float previousActualSpeed = this.speed;
        boolean remainsExternallyDriven = targetSpeed != 0.0F && hasSource() &&
                Math.abs(previousActualSpeed) >= Math.abs(targetSpeed) &&
                Math.signum(previousActualSpeed) == Math.signum(targetSpeed);
        boolean stableGeneratedTopology = targetSpeed != 0.0F && !hasSource() && hasNetwork() &&
                Float.compare(previousActualSpeed, targetSpeed) == 0;
        boolean canRetuneGeneratedTopology = targetSpeed != 0.0F && !hasSource() && hasNetwork() &&
                previousActualSpeed != 0.0F;
        if (!forceTopologyRebuild && targetSpeed == 0.0F && hasSource()) {
            // Keep the externally driven member in place, but remove its generator contribution while the old applied
            // speed still identifies it as a source. Removing/re-adding the whole member would transiently orphan a
            // partially loaded network, so only the source entry changes here.
            replaceSourceContribution(0.0F);
        } else if (!forceTopologyRebuild && (remainsExternallyDriven || stableGeneratedTopology)) {
            replaceSourceContribution(targetSpeed);
        } else if (canRetuneGeneratedTopology) {
            retuneGeneratedTopology(targetSpeed);
        } else {
            rebuildGeneratedTopology(targetSpeed, forceTopologyRebuild);
        }

        refreshNetworkContributions();
        if (Float.compare(previousActualSpeed, this.speed) != 0) {
            this.onSpeedChanged(previousActualSpeed);
        }
        this.setChanged();
        this.sendData();
    }

    /**
     * Atomically replaces only this member's source contribution while preserving its current external source and
     * network membership. The old source entry is removed before {@link #appliedGeneratedSpeed} changes; the new value
     * is visible before the entry is added again.
     */
    private void replaceSourceContribution(float targetSpeed) {
        KineticNetwork network = hasNetwork() ? getOrCreateNetwork() : null;
        if (network != null) {
            network.sources.remove(this);
        }
        appliedGeneratedSpeed = targetSpeed;
        if (network == null) {
            return;
        }
        if (targetSpeed != 0.0F) {
            network.updateCapacityFor(this, calculateAddedStressCapacity());
        } else {
            lastCapacityProvided = 0.0F;
            network.updateCapacity();
        }
    }

    /**
     * Changes a running independent source without replacing its network object. Create stores the contributions of
     * unloaded members only in that object, so clearing the network id here would discard their aggregate stress and
     * capacity until every chunk happened to load again.
     */
    private void retuneGeneratedTopology(float targetSpeed) {
        KineticNetwork network = getOrCreateNetwork();
        detachKinetics();
        network.sources.remove(this);
        appliedGeneratedSpeed = targetSpeed;
        setSpeed(targetSpeed);
        network.updateCapacityFor(this, calculateAddedStressCapacity());
        attachKinetics();
    }

    /**
     * Rebuilds this block entity as an independent generator (or stops it). Detachment and old-network removal happen
     * while the old applied speed is still visible. The target applied speed is published before joining a new network.
     */
    private void rebuildGeneratedTopology(float targetSpeed, boolean attachStoppedSource) {
        KineticNetwork previousNetwork = hasNetwork() ? getOrCreateNetwork() : null;
        if (previousNetwork != null) {
            // Chunk unload deliberately leaves the old block-entity identity in Create's network. A newly loaded
            // instance is added alongside it; force one identity cleanup before removing this instance, otherwise the
            // target-zero path can strand a phantom source/member in an otherwise dead network.
            previousNetwork.updateNetwork();
        }
        if (previousNetwork != null && appliedGeneratedSpeed == 0.0F) {
            // Clean up a zero-valued stale source entry too; KineticNetwork.remove() only removes sources when
            // isSource() is true.
            previousNetwork.sources.remove(this);
        }
        if (this.speed != 0.0F) {
            detachKinetics();
        }
        setNetwork(null);
        source = null;
        setSpeed(0.0F);

        appliedGeneratedSpeed = targetSpeed;
        if (targetSpeed == 0.0F) {
            lastCapacityProvided = 0.0F;
            if (attachStoppedSource) {
                attachKinetics();
            }
            return;
        }

        setSpeed(targetSpeed);
        setNetwork(createNetworkId());
        attachKinetics();
    }

    private void refreshNetworkContributions() {
        if (!hasNetwork()) {
            return;
        }
        KineticNetwork network = getOrCreateNetwork();
        if (appliedGeneratedSpeed != 0.0F) {
            network.updateCapacityFor(this, calculateAddedStressCapacity());
        }
        network.updateStressFor(this, calculateStressApplied());
        network.updateStress();
    }

    @Override
    public float calculateStressApplied() {
        float impact = (float) IBlockStressValues.getImpact(this.getStressConfigKey());
        this.lastStressApplied = impact;
        return impact;
    }

    @Override
    public float calculateAddedStressCapacity() {
        float capacity = (float) IBlockStressValues.getCapacity(this.getStressConfigKey());
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    public Long createNetworkId() {
        return this.worldPosition.asLong();
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        IMachineBlockEntity.super.saveCustomPersistedData(tag, forDrop);
        if (forDrop) {
            // A picked-up or cloned output may retain inventory fields, but never a live network request/source state.
            tag.remove(WORKING_SPEED_NBT_KEY);
            tag.remove(APPLIED_SPEED_NBT_KEY);
            tag.remove(SOURCE_SUSPENDED_NBT_KEY);
        }
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        if (!Float.isFinite(workingSpeed)) workingSpeed = 0.0F;
        if (!Float.isFinite(appliedGeneratedSpeed)) {
            appliedGeneratedSpeed = 0.0F;
            corruptKineticStatePending = true;
        }
        IMachineBlockEntity.super.loadCustomPersistedData(tag);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return ManagedFieldHolderMap.getManagedFieldHolder(getClass());
    }

    @Override
    public void setChanged() {
        if (getLevel() != null) {
            getLevel().blockEntityChanged(getBlockPos());
        }
    }

    @Override
    public void onChanged() {
        var level = getLevel();
        if (level != null && !level.isClientSide && level.getServer() != null) {
            level.getServer().execute(this::setChanged);
        }
    }
}
