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
import com.mo_guang.ctpp.client.SplitShaftVisual;
import com.mo_guang.ctpp.common.machine.IKineticMachine;
import com.mo_guang.ctpp.common.machine.multiblock.part.KineticPartMachine;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticEffectHandler;
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

    private final long offset = GTValues.RNG.nextInt(20);
    @Persisted
    @DescSynced
    public float workingSpeed;
    public boolean reActivateSource;
    /**
     * 重载宽限期：GTCEu 不持久化多方块成型状态，重载后需要异步重检（约 1~1.5s）才能恢复。
     * 在此期间保留持久化的 workingSpeed 重新挂接 Create 网络，避免应力网络失去源而整体崩溃。
     */
    private static final int GRACE_TICKS = 100;
    @DescSynced
    private boolean graceActive;
    private int graceTicks;

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
                                // SplitShaftVisual draws a half shaft per end of the axis and asks the machine
                                // whether each end is actually wanted, so a shop whose shaft only enters from
                                // behind does not render a stub through its shopfront. Create's plain shaft
                                // visual draws one full-length cylinder regardless of the machine's faces.
                                SimpleBlockEntityVisualizer.builder(type)
                                        .factory(SplitShaftVisual::new)
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
        if (!level.isClientSide && getDefinition().isSource() && workingSpeed != 0 &&
                metaMachine instanceof KineticPartMachine) {
            // 方案 B：保留 NBT 里持久化的 Create 动能状态（speed/source/network），
            // 由 Create 的 initialize()/attachKinetics() 自行恢复原网络，
            // 避免进游戏后应力网络失去源而整体崩溃。
            // 宽限期只负责在 GTCEu 异步重检成型（约 1~1.5s）期间对源保持乐观判定。
            graceActive = true;
            graceTicks = GRACE_TICKS;
            reActivateSource = true;
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
            if (!simulate) {
                workingSpeed = speed;
                reActivateSource = true;
            }
            return speed * getDefinition().getTorque();
        }
        return 0;
    }

    public void scheduleWorking(float su) {
        scheduleWorking(su, false);
    }

    public void stopWorking() {
        if (getDefinition().isSource() && workingSpeed != 0) {
            workingSpeed = 0;
            reActivateSource = true;
        }
    }

    @Override
    public float getGeneratedSpeed() {
        return isValidOutputSource() ? workingSpeed : 0;
    }

    public boolean isGraceActive() {
        return graceActive;
    }

    /**
     * 宽限期内乐观信任持久化的 workingSpeed，避免重载后 GTCEu 异步重检成型完成前
     * 应力网络短暂失去源；重检结束后立即恢复严格判定。
     */
    private boolean isValidOutputSource() {
        if (graceActive) {
            return true;
        }
        // 只有应力输出部件需要按成型状态门控：未成型时不得继续充当网络源。
        return !(metaMachine instanceof KineticPartMachine) || isKineticPartFormed();
    }

    private boolean isKineticPartFormed() {
        return metaMachine instanceof KineticPartMachine kineticPartMachine &&
                kineticPartMachine.isValidOutputBinding();
    }

    protected void notifyStressCapacityChange(float capacity) {
        this.getOrCreateNetwork().updateCapacityFor(this, capacity);
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
        if (!level.isClientSide && getDefinition().isSource()) {
            tickGracePeriod();
            // workingSpeed 是 @DescSynced 服务器权威字段，客户端不允许本地写：
            // 客户端写会污染同步值，且服务端值不变时不会重发，可能把转速卡在 0。
            if (!isValidOutputSource()) {
                stopWorking();
            }
        }
        super.tick();
        if (getDefinition().isSource() && reActivateSource) {
            updateGeneratedRotation();
            reActivateSource = false;
        }
    }

    /**
     * 重载宽限期倒计时：异步重检已成型则提前结束，超时则按未成型处理（由 tick 停机）。
     */
    private void tickGracePeriod() {
        if (!graceActive) {
            return;
        }
        if (--graceTicks <= 0 || isKineticPartFormed()) {
            graceActive = false;
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
        if (!getDefinition().isSource() || level == null || level.isClientSide) {
            return;
        }
        float speed = this.getGeneratedSpeed();
        float prevSpeed = this.speed;
        if (prevSpeed != speed) {
            if (!this.hasSource()) {
                IRotate.SpeedLevel levelBefore = IRotate.SpeedLevel.of(this.speed);
                IRotate.SpeedLevel levelAfter = IRotate.SpeedLevel.of(speed);
                if (levelBefore != levelAfter) {
                    this.effects.queueRotationIndicators();
                }
            }
            this.applyNewSpeed(prevSpeed, speed);
        }
        if (this.hasNetwork() && speed != 0.0F) {
            KineticNetwork network = this.getOrCreateNetwork();
            this.notifyStressCapacityChange(this.calculateAddedStressCapacity());
            this.getOrCreateNetwork().updateStressFor(this, this.calculateStressApplied());
            network.updateStress();
        }
        this.onSpeedChanged(prevSpeed);
        this.sendData();
    }

    @Override
    public float calculateStressApplied() {
        if (!isValidOutputSource()) {
            this.lastStressApplied = 0;
            return 0;
        }
        float impact = (float) IBlockStressValues.getImpact(this.getStressConfigKey());
        this.lastStressApplied = impact;
        return impact;
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (!isValidOutputSource()) {
            this.lastCapacityProvided = 0;
            return 0;
        }
        float capacity = (float) IBlockStressValues.getCapacity(this.getStressConfigKey());
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    public void applyNewSpeed(float prevSpeed, float speed) {
        if (speed == 0.0F) {
            if (this.hasSource()) {
                this.notifyStressCapacityChange(0.0F);
                this.getOrCreateNetwork().updateStressFor(this, this.calculateStressApplied());
            } else {
                this.detachKinetics();
                this.setSpeed(0.0F);
                this.setNetwork(null);
            }
        } else if (prevSpeed == 0.0F) {
            this.setSpeed(speed);
            this.setNetwork(this.createNetworkId());
            this.attachKinetics();
        } else if (this.hasSource()) {
            if (Math.abs(prevSpeed) >= Math.abs(speed) && Math.signum(prevSpeed) == Math.signum(speed)) {
                return;
            }
            // 应力箱是 GT 驱动的多方块部件，方向冲突只会来自重载/未成型竞态，
            // 不触发原版“反向即销毁”的保护，改为重新挂载以同步到新目标速度。
            if (Math.abs(prevSpeed) >= Math.abs(speed) && !(metaMachine instanceof KineticPartMachine)) {
                this.level.destroyBlock(this.worldPosition, true);
                return;
            }
            this.detachKinetics();
            this.setSpeed(speed);
            this.source = null;
            this.setNetwork(this.createNetworkId());
            this.attachKinetics();
        } else {
            this.detachKinetics();
            this.setSpeed(speed);
            this.attachKinetics();
        }
    }

    public Long createNetworkId() {
        return this.worldPosition.asLong();
    }

    // @Override
    // protected void write(CompoundTag compound, boolean clientPacket) {
    // super.write(compound, clientPacket);
    // compound.putFloat("workingSpeed", workingSpeed);
    // }
    //
    // @Override
    // protected void read(CompoundTag compound, boolean clientPacket) {
    // super.read(compound, clientPacket);
    // workingSpeed = compound.contains("workingSpeed") ? compound.getFloat("workingSpeed") : 0;
    // }

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
