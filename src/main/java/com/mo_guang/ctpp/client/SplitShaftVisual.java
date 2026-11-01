package com.mo_guang.ctpp.client;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

import com.mo_guang.ctpp.common.blockentity.KineticMachineBlockEntity;
import com.mo_guang.ctpp.common.machine.IKineticMachine;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * @author KilaBash
 * @date 2023/4/1
 * @implNote SplitShaftInstance
 */
public class SplitShaftVisual extends KineticBlockEntityVisual<KineticMachineBlockEntity> {

    protected final ArrayList<RotatingInstance> keys;

    public SplitShaftVisual(VisualizationContext context, KineticMachineBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        keys = new ArrayList<>(2);

        float speed = blockEntity.getSpeed();

        for (Direction dir : Iterate.directionsInAxis(rotationAxis())) {

            RotatingInstance half = instancerProvider()
                    .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF)).createInstance();

            float splitSpeed = speed * (blockEntity.getMetaMachine() instanceof IKineticMachine kineticMachine ?
                    kineticMachine.getRotationSpeedModifier(dir) : 1);
            boolean visible = hasShaft(blockEntity, dir);
            half.setup(blockEntity, splitSpeed)
                    .setPosition(getVisualPosition()).rotateToFace(Direction.SOUTH, dir)
                    .setChanged();
            // setVisible returns void, so it cannot join the builder chain.
            half.setVisible(visible);
            keys.add(half);
        }
    }

    @Override
    public void update(float pt) {
        Block block = blockState.getBlock();
        final Direction.Axis boxAxis = ((IRotate) block).getRotationAxis(blockState);

        Direction[] directions = Iterate.directionsInAxis(boxAxis);

        for (int i : Iterate.zeroAndOne) {
            Direction dir = directions[i];
            float speed = blockEntity.getSpeed() *
                    (blockEntity.getMetaMachine() instanceof IKineticMachine kineticMachine ?
                            kineticMachine.getRotationSpeedModifier(dir) : 1);
            RotatingInstance half = keys.get(i);
            half.setup(blockEntity, speed).setChanged();
            half.setVisible(hasShaft(blockEntity, dir));
        }
    }

    /**
     * Whether the machine actually wants a shaft stub on this face.
     * <p>
     * A half shaft is instanced for both ends of the rotation axis, so a machine that takes rotation on only one
     * side — the trading machine, whose gearbox is on the back — would otherwise show a stub straight through its
     * front. Zero speed is not enough to hide it, because the geometry would still be drawn; the instance itself
     * has to be made invisible.
     */
    private static boolean hasShaft(KineticMachineBlockEntity blockEntity, Direction dir) {
        return !(blockEntity.getMetaMachine() instanceof IKineticMachine kineticMachine) ||
                kineticMachine.hasShaftTowards(dir);
    }

    @Override
    protected void _delete() {
        keys.forEach(RotatingInstance::delete);
        keys.clear();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        keys.forEach(consumer);
    }

    @Override
    public void updateLight(float v) {
        keys.forEach(rotatingInstance -> relight(pos, rotatingInstance));
    }
}
