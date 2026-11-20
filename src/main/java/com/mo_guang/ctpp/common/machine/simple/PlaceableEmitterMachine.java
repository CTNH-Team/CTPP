package com.mo_guang.ctpp.common.machine.simple;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IMachineModifyDrops;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.ctnhlang.CN;
import com.ctnhlang.EN;
import com.ctnhlang.Key;
import com.mo_guang.ctpp.common.beam.BeamChunkIndex;
import com.mo_guang.ctpp.common.beam.EmitterBeamTracker;
import com.mo_guang.ctpp.common.beam.IBeamRedirector;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * A placeable GregTech emitter: accepts EU from its back face, and shoots a beam from its front face
 * that transfers energy to the block it hits. Port of greg-emitters semantics onto the GT machine API.
 * Beam rendering is global (see {@link EmitterBeamTracker}), independent of the emitter's visibility.
 */
public class PlaceableEmitterMachine extends TieredEnergyMachine
                                     implements IFancyUIMachine, IMachineLife, IMachineModifyDrops {

    /** Max emission current, in amps; the UI slider sets the actual value and caps everything the beam does. */
    public static final int MAX_CONSUMPTION = 4;
    /** Beam damage per voltage tier. Placeholder; adjust freely. */
    public static final float DAMAGE_PER_TIER = 2.0f;
    /** Max mirror bounces along one beam path. Placeholder; adjust freely. */
    public static final int MAX_REFLECTIONS = 8;
    /** Fraction of the remaining voltage kept after each mirror reflection. Placeholder; adjust freely. */
    public static final double REFLECTION_VOLTAGE_KEEP = 0.95;
    /** Entity scan granularity along the beam, in blocks (keeps entity AABB lookups tight). */
    private static final double ENTITY_SCAN_SLICE = 16;
    private static final double MAX_BEND = Math.PI / 2;

    /** Angle from the default direction, in radians. Limited to 90 degrees. */
    @Persisted
    @DescSynced
    @Getter
    public double zenith;

    /** Rotation around the default direction, in radians. */
    @Persisted
    @DescSynced
    @Getter
    public double azimuth;

    @Persisted
    @DescSynced
    @Getter
    public boolean transferDisabled;

    /** Emission current the beam fires with (UI-adjustable, 1..MAX_CONSUMPTION): the max output amperage. */
    @Persisted
    @DescSynced
    @Getter
    public int consumptionAmps = 1;

    private int beamId = -1;

    // last state sent to the tracker, to avoid re-sending identical packets
    private List<Vec3> sentPoints = List.of();
    private long sentVoltage = -1;
    private long sentAmps = -1;

    @Nullable
    protected TickableSubscription beamSubs;

    public PlaceableEmitterMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier);
        energyContainer.setSideInputCondition(side -> side != getFrontFacing());
    }

    @Override
    protected NotifiableEnergyContainer createEnergyContainer(Object... args) {
        long voltage = GTValues.V[getTier()];
        return new NotifiableEnergyContainer(this, voltage * 64 * MAX_CONSUMPTION, voltage, MAX_CONSUMPTION, 0, 0);
    }

    //////////////////////////////////////
    // ******** Beam tick **********//
    //////////////////////////////////////
    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) beamSubs = subscribeServerTick(this::beamTick);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (beamSubs != null) {
            beamSubs.unsubscribe();
            beamSubs = null;
        }
        removeBeam();
    }

    @Override
    public void onMachineRemoved() {
        removeBeam();
    }

    public void beamTick() {
        if (!(getLevel() instanceof ServerLevel level)) return;
        long stored = energyContainer.getEnergyStored();
        if (stored <= 0 || transferDisabled) {
            removeBeam(level);
            return;
        }

        // an emitter always fires at its own tier voltage, regardless of the feeding network's
        // voltage: beam color bands then reliably show this emitter's tier decaying with distance
        long voltage = GTValues.V[getTier()];
        // emission current: the UI setting IS the max output amperage by design; storage decides
        // how much of it can actually flow this tick. The buffer bursts like any GT machine, so a
        // starving supply thins the beam (via stored/voltage) and the slider thickens it directly.
        long amps = Math.min(consumptionAmps, stored / voltage);
        if (amps <= 0) {
            energyContainer.removeEnergy(stored); // not enough for one packet; drain the rest
            removeBeam(level);
            return;
        }

        // ---- path tracing ----
        // The beam flies until its decayed voltage reaches 1 (dissipationDistance), bouncing off
        // mirrors up to MAX_REFLECTIONS times. Collisions are only computed inside loaded chunks
        // (BeamChunkIndex): the beam passes unloaded space unimpeded and resumes colliding when it
        // re-enters a loaded chunk.
        int tier = getTier();
        double maxRange = dissipationDistance(voltage, tier);
        double bouncePenalty = bouncePenaltyBlocks(tier);
        List<Vec3> points = new ArrayList<>(4);
        Vec3 pos = getPos().getCenter().add(beamDirection().scale(0.51));
        points.add(pos);
        Vec3 dir = beamDirection();
        double traveled = 0; // decay distance: geometric length + per-bounce penalties
        LivingEntity hitEntity = null;
        BlockHitResult targetHit = null;

        for (int bounce = 0; bounce <= MAX_REFLECTIONS; bounce++) {
            double budget = maxRange - traveled;
            if (budget <= 0) break;

            // nearest block collision within loaded chunks along this segment
            BlockHitResult blockHit = null;
            double blockT = budget;
            for (double[] run : BeamChunkIndex.loadedRuns(level, pos, dir, budget)) {
                if (run[0] >= blockT) break; // runs are distance-ordered; the hit is already found
                Vec3 a = pos.add(dir.scale(run[0]));
                Vec3 b = pos.add(dir.scale(Math.min(run[1], blockT)));
                BlockHitResult hit = level.clip(new ClipContext(a, b,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    double t = run[0] + a.distanceTo(hit.getLocation());
                    if (t < blockT) {
                        blockT = t;
                        blockHit = hit;
                    }
                }
            }

            // nearest living entity along the segment, sliced so the lookup AABB stays tight
            // (entity lookup uses the entity section storage and never touches unloaded chunks)
            LivingEntity entityHit = null;
            double entityT = blockT;
            for (double s = 0; s < entityT; s += ENTITY_SCAN_SLICE) {
                Vec3 a = pos.add(dir.scale(s));
                Vec3 b = pos.add(dir.scale(Math.min(entityT, s + ENTITY_SCAN_SLICE)));
                var box = new AABB(a, b).inflate(1.5);
                for (var entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
                    var clip = entity.getBoundingBox().inflate(0.25).clip(a, b);
                    if (clip.isPresent()) {
                        double t = s + a.distanceTo(clip.get());
                        if (t < entityT) {
                            entityT = t;
                            entityHit = entity;
                        }
                    }
                }
            }

            double segLen = Math.min(entityT, blockT);
            traveled += segLen;
            pos = pos.add(dir.scale(segLen));
            points.add(pos);

            if (entityHit != null) {
                hitEntity = entityHit;
                break;
            }
            if (blockHit == null) break; // dissipated mid-air

            // a block was hit: mirrors redirect the beam, anything else terminates the path
            var state = level.getBlockState(blockHit.getBlockPos());
            Vec3 reflected = state.getBlock() instanceof IBeamRedirector redirector && bounce < MAX_REFLECTIONS ?
                    redirector.redirect(dir, state) : null;
            if (reflected == null) {
                targetHit = blockHit;
                break;
            }
            dir = reflected.normalize();
            traveled += bouncePenalty;
            pos = pos.add(dir.scale(1.0E-3)); // step out of the mirror's own collision box
        }

        if (hitEntity != null) {
            // burn the emission current; damage scales with voltage tier
            energyContainer.removeEnergy(amps * voltage);
            hitEntity.hurt(level.damageSources().magic(), DAMAGE_PER_TIER * tier);
            updateBeam(level, points, voltage, amps);
            return;
        }

        if (targetHit != null) {
            BlockPos target = targetHit.getBlockPos();
            if (!target.equals(getPos())) {
                Direction side = targetHit.getDirection();
                // the beam's voltage decays with travel distance; the target only receives what arrives
                long effectiveVoltage = Math.max(1, Math.round(voltageAt(voltage, tier, traveled)));
                IEnergyContainer container = GTCapabilityHelper.getEnergyContainer(level, target, side);
                if (container != null && container.inputsEnergy(side)) {
                    long accepted = container.acceptEnergyFromNetwork(side, effectiveVoltage, amps);
                    if (accepted > 0) {
                        // voltage decay is a real loss in transit: the emitter pays the full input
                        // voltage for every accepted ampere; amperage itself is never lost.
                        energyContainer.removeEnergy(accepted * voltage);
                        updateBeam(level, points, voltage, accepted);
                        return;
                    }
                }
            }
        }

        // no usable target: always burn the emission current anyway
        energyContainer.removeEnergy(amps * voltage);
        updateBeam(level, points, voltage, amps);
    }

    private void updateBeam(ServerLevel level, List<Vec3> points, long voltage, long amps) {
        if (beamId < 0) beamId = EmitterBeamTracker.newBeamId();
        if (voltage == sentVoltage && amps == sentAmps && pointsEqual(sentPoints, points)) {
            // the beam is unchanged but alive: heartbeat it so the tracker's orphan sweep
            // doesn't collect it
            EmitterBeamTracker.touchBeam(level, beamId);
            return;
        }
        sentPoints = points;
        sentVoltage = voltage;
        sentAmps = amps;
        EmitterBeamTracker.setBeam(level, beamId, points, voltage, amps, getTier());
    }

    private static boolean pointsEqual(List<Vec3> a, List<Vec3> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Vec3 pa = a.get(i), pb = b.get(i);
            if (pa.x != pb.x || pa.y != pb.y || pa.z != pb.z) return false;
        }
        return true;
    }

    private void removeBeam(ServerLevel level) {
        if (beamId < 0) return;
        EmitterBeamTracker.removeBeam(level, beamId);
        beamId = -1;
        sentPoints = List.of();
        sentVoltage = -1;
        sentAmps = -1;
    }

    private void removeBeam() {
        if (getLevel() instanceof ServerLevel serverLevel) removeBeam(serverLevel);
    }

    //////////////////////////////////////
    // ******** Direction **********//
    //////////////////////////////////////
    public Vec3 beamDirection() {
        Direction facing = getFrontFacing();
        Vec3 base = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        if (zenith == 0 && azimuth == 0) return base;
        Vec3 up = Math.abs(base.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = base.cross(up).normalize();
        Vec3 vertical = side.cross(base).normalize();
        double sinZenith = Math.sin(zenith);
        return base.scale(Math.cos(zenith))
                .add(side.scale(Math.sin(azimuth) * sinZenith))
                .add(vertical.scale(Math.cos(azimuth) * sinZenith))
                .normalize();
    }

    // zenith / azimuth / transferDisabled / consumptionAmps are managed fields: LDLib's ref update pass
    // detects the value change and drives both the sync packet and onChanged() -> markDirty() by itself.
    public void adjustAngle(double zenithDelta, double azimuthDelta) {
        zenith = Mth.clamp(zenith + zenithDelta, -MAX_BEND, MAX_BEND);
        azimuth = (azimuth + azimuthDelta) % (Math.PI * 2);
        if (azimuth < 0) azimuth += Math.PI * 2;
    }

    public void setAngles(double zenith, double azimuth) {
        this.zenith = Mth.clamp(zenith, -MAX_BEND, MAX_BEND);
        azimuth %= Math.PI * 2;
        this.azimuth = azimuth < 0 ? azimuth + Math.PI * 2 : azimuth;
    }

    public void setTransferDisabled(boolean disabled) {
        transferDisabled = disabled;
    }

    public void adjustConsumption(int delta) {
        consumptionAmps = Mth.clamp(consumptionAmps + delta, 1, MAX_CONSUMPTION);
    }

    public boolean isBeamActive() {
        return !transferDisabled && energyContainer.getEnergyStored() > 0;
    }

    //////////////////////////////////////
    // ******** Interaction **********//
    //////////////////////////////////////
    /**
     * Never open the UI while holding a GT tool (wrench, screwdriver, ...): tool clicks are
     * machine interactions of their own, and opening the UI on top of them makes wrench
     * rotation look broken.
     */
    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return ToolHelper.getToolTypes(player.getItemInHand(hand)).isEmpty();
    }

    /**
     * The emitter has no auto-output sides for the wrench to configure, so the wrench is purely
     * a rotation tool here: clicking any face aims the emitter at it, with or without shift
     * (GT only rotates on shift-click, which reads as "rotation does nothing" on a machine
     * without wrench configuration). Clicking the current front face is rejected, as a machine
     * cannot face itself.
     */
    @Override
    protected InteractionResult onWrenchClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                              BlockHitResult hitResult) {
        if (gridSide == getFrontFacing() || !isFacingValid(gridSide)) {
            return InteractionResult.FAIL;
        }
        if (!isRemote()) {
            setFrontFacing(gridSide);
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    //////////////////////////////////////
    // ******** Beam appearance **********//
    //////////////////////////////////////
    /** Fraction of the remaining voltage lost per block of beam travel. */
    public static double lossPerBlock(int tier) {
        return 0.5 / Math.pow(2, tier - 1);
    }

    /** Effective beam voltage after traveling {@code distance} blocks from {@code baseVoltage}. */
    public static double voltageAt(long baseVoltage, int tier, double distance) {
        return baseVoltage * Math.pow(1 - lossPerBlock(tier), distance);
    }

    /**
     * Distance over which the beam's voltage decays to 1 EU and the beam dissolves; there is no
     * other range limit. Bounces shorten the reach via {@link #bouncePenaltyBlocks}.
     */
    public static double dissipationDistance(double baseVoltage, int tier) {
        return Math.log(baseVoltage) / -Math.log(1 - lossPerBlock(tier));
    }

    /**
     * Decay-distance equivalent of one mirror reflection. Because decay is exponential, a fixed
     * voltage multiplier maps to a fixed extra distance: v * KEEP == decay(d + penalty).
     */
    public static double bouncePenaltyBlocks(int tier) {
        return Math.log(REFLECTION_VOLTAGE_KEEP) / Math.log(1 - lossPerBlock(tier));
    }

    /** Continuous spectrum position across GT tier main colors (VCM) for an arbitrary voltage. */
    public static int colorForVoltage(double voltage) {
        // GT voltages grow by 4x per tier (V[t] = 8 * 4^t), so the spectrum position is log4-based;
        // exact tier voltages land exactly on their tier color.
        double x = Math.log(Math.max(voltage, 1) / GTValues.V[0]) / Math.log(4);
        x = Mth.clamp(x, 0, GTValues.VCM.length - 1.001);
        int lo = (int) Math.floor(x);
        int hi = Math.min(lo + 1, GTValues.VCM.length - 1);
        float t = (float) (x - lo);
        int c1 = GTValues.VCM[lo];
        int c2 = GTValues.VCM[hi];
        int r = Mth.lerpInt(t, c1 >> 16 & 255, c2 >> 16 & 255);
        int g = Mth.lerpInt(t, c1 >> 8 & 255, c2 >> 8 & 255);
        int b = Mth.lerpInt(t, c1 & 255, c2 & 255);
        return r << 16 | g << 8 | b;
    }

    //////////////////////////////////////
    // ******** Drops **********//
    //////////////////////////////////////
    /** The vanilla GT emitter item corresponding to a tier; placed emitters drop this, not the machine item. */
    public static ItemStack emitterItem(int tier) {
        var entry = switch (tier) {
            case 1 -> GTItems.EMITTER_LV;
            case 2 -> GTItems.EMITTER_MV;
            case 3 -> GTItems.EMITTER_HV;
            case 4 -> GTItems.EMITTER_EV;
            case 5 -> GTItems.EMITTER_IV;
            case 6 -> GTItems.EMITTER_LuV;
            case 7 -> GTItems.EMITTER_ZPM;
            case 8 -> GTItems.EMITTER_UV;
            // UHV+ component items are null when GTCEu's high-tier content is disabled
            case 9 -> GTItems.EMITTER_UHV;
            case 10 -> GTItems.EMITTER_UEV;
            case 11 -> GTItems.EMITTER_UIV;
            case 12 -> GTItems.EMITTER_UXV;
            case 13 -> GTItems.EMITTER_OpV;
            default -> null;
        };
        return entry != null ? entry.asStack() : ItemStack.EMPTY;
    }

    @Override
    public void onDrops(List<ItemStack> drops) {
        // breaking a placed emitter returns the vanilla GT emitter item used to place it;
        // without one (high-tier content disabled) keep the machine's own default drop
        var stack = emitterItem(getTier());
        if (!stack.isEmpty()) {
            drops.clear();
            drops.add(stack);
        }
    }

    //////////////////////////////////////
    // ******** GUI **********//
    //////////////////////////////////////
    @Key("ctnh.placeable_emitter.consumption")
    @CN("发射电流")
    @EN("Current")
    public static Lang consumptionLabel;

    @Key("ctnh.placeable_emitter.transfer_off")
    @CN("传输关")
    @EN("Off")
    public static Lang transferOffLabel;

    @Key("ctnh.placeable_emitter.transfer_on")
    @CN("传输开")
    @EN("On")
    public static Lang transferOnLabel;

    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 140, 172);
        group.addWidget(new LabelWidget(5, 5, getBlockState().getBlock().getDescriptionId()));
        group.addWidget(new LabelWidget(5, 17,
                () -> String.format("%.1f°, %.1f°", Math.toDegrees(zenith), Math.toDegrees(azimuth))));
        group.addWidget(new ProgressWidget(
                () -> energyContainer.getEnergyStored() * 1d / energyContainer.getEnergyCapacity(), 5, 30, 18, 100,
                new ProgressTexture(IGuiTexture.EMPTY, GuiTextures.ENERGY_BAR_BASE))
                .setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP)
                .setBackground(GuiTextures.ENERGY_BAR_BACKGROUND));
        // polar angle dial (greg-emitters style): drag the red handle to aim
        group.addWidget(new com.mo_guang.ctpp.common.gui.widget.EmitterAngleDialWidget(this, 22, 30, 48));
        // cardinal labels around the dial (Minecraft yaw convention)
        group.addWidget(new LabelWidget(70, 32, "W"));
        group.addWidget(new LabelWidget(122, 78, "N"));
        group.addWidget(new LabelWidget(70, 124, "E"));
        group.addWidget(new LabelWidget(20, 78, "S"));
        // always-on consumption control (1..MAX_CONSUMPTION amps)
        group.addWidget(new LabelWidget(5, 140, consumptionLabel.translate()));
        group.addWidget(new ButtonWidget(5, 150, 20, 20, new TextTexture("-"),
                data -> adjustConsumption(-1)));
        group.addWidget(new LabelWidget(30, 156, () -> consumptionAmps + " A"));
        group.addWidget(new ButtonWidget(50, 150, 20, 20, new TextTexture("+"),
                data -> adjustConsumption(1)));
        group.addWidget(new SwitchWidget(85, 146, 52, 22, (clickData, value) -> setTransferDisabled(!value))
                .setTexture(
                        new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON,
                                new TextTexture(transferOffLabel.translate().getString())),
                        new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON,
                                new TextTexture(transferOnLabel.translate().getString())))
                .setPressed(!transferDisabled));
        return group;
    }
}
