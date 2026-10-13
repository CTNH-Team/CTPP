package com.mo_guang.ctpp.common.machine.multiblock;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import com.mo_guang.ctpp.api.pattern.StaticBlockPattern;
import com.mo_guang.ctpp.dynamicPart.rotation.*;
import lombok.Getter;
import lombok.Setter;
import tech.vixhentx.mcmod.ctnhlib.utils.MachineUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComplexRotatingMachine extends WorkableElectricMultiblockMachine
                                    implements IContraptionMultiblock<RubiksCubeContraptionEntity> {

    @Getter
    @Setter
    public List<RubiksCubeContraptionEntity> contraptionEntity = new ArrayList<>();
    public List<String> avalibleMoving = List.of("U", "U'", "D", "D'", "L", "L'", "R", "R'", "F", "F'", "B", "B'");
    protected TickableSubscription rotatingSubs;
    private static final int MOVE_INTERVAL_TICKS = 40;
    public String rotation = "STOP";

    public ComplexRotatingMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (contraptionEntity.isEmpty()) {
            var rotatingEntities = assemble(MachineUtils.getOffset(this, 0, 0, 1));
            if (rotatingEntities != null) {
                this.contraptionEntity.addAll(rotatingEntities.values());
            }
        }
        this.rotatingSubs = this.subscribeServerTick(rotatingSubs, this::rotatingTick);
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        // disassemble and clear using interface helper
        clearAndDisassembleRotatingEntities();
        unsubscribeRotatingTick();
    }

    @Override
    protected void onStructureRevalidationChanged(boolean pending) {
        super.onStructureRevalidationChanged(pending);
        if (pending) {
            unsubscribeRotatingTick();
        }
    }

    @Override
    public void onUnload() {
        unsubscribeRotatingTick();
        super.onUnload();
    }

    private void unsubscribeRotatingTick() {
        if (rotatingSubs != null) {
            unsubscribe(rotatingSubs);
            rotatingSubs = null;
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        var button = Component.literal("旋转：");
        button.append(" ");
        button.append(ComponentPanelWidget.withButton(Component.literal("U"), "U"));
        button.append(" ");
        button.append(ComponentPanelWidget.withButton(Component.literal("L"), "L"));
        button.append(" ");
        button.append(ComponentPanelWidget.withButton(Component.literal("R"), "R"));
        button.append(" ");
        button.append(ComponentPanelWidget.withButton(Component.literal("F"), "F"));
        textList.add(button);
    }

    @Override
    public void handleDisplayClick(String componentData, ClickData clickData) {
        if (!clickData.isRemote) {
            rotation = componentData;
        }
    }

    public void rotatingTick() {
        if (!isStructureOperational() || contraptionEntity == null || contraptionEntity.isEmpty()) {
            return;
        }

        // A command is sent to all eight pieces as one cube move. Never start
        // another move while one piece is still interpolating to its target.
        if (contraptionEntity.stream().anyMatch(RubiksCubeContraptionEntity::isMoving)) {
            return;
        }

        String requestedMove = rotation;
        if (!"STOP".equals(requestedMove)) {
            rotation = "STOP";
            performMove(requestedMove);
            return;
        }

        if (getOffsetTimer() % MOVE_INTERVAL_TICKS == 0) {
            int index = RandomSource.create().nextInt(avalibleMoving.size());
            performMove(avalibleMoving.get(index));
        }
    }

    private void performMove(String move) {
        contraptionEntity.forEach(entity -> {
            if (entity != null) {
                entity.performStandardMove(move);
            }
        });
    }

    @Override
    public Map<Integer, RubiksCubeContraptionEntity> assemble(BlockPos pivot) {
        if (self().getLevel() instanceof TrackedDummyWorld) return null;
        Map<Integer, RubiksCubeContraptionEntity> ce = new HashMap<>();
        var pattern = self().getDefinition().getPatternFactory().get();
        if (pattern instanceof StaticBlockPattern staticBlockPattern) {
            Map<Integer, List<BlockPos>> dymanicPart = staticBlockPattern.getDynamicPart(self().getMultiblockState());
            for (var entry : dymanicPart.entrySet()) {
                int group = entry.getKey();
                var part = entry.getValue();
                BlockPos pos = pivot;
                BlockPos randomPos = part.get(0);
                pos = pos.offset((int) Math.signum(randomPos.getX() - pivot.getX()),
                        (int) Math.signum(randomPos.getY() - pivot.getY()),
                        (int) Math.signum(randomPos.getZ() - pivot.getZ()));
                SimpleRotatingContraption contraption = new SimpleRotatingContraption(part, pivot);
                contraption.assemble(this.self().getLevel(), self().getPos());
                contraption.removeBlocksFromWorld(this.self().getLevel(), BlockPos.ZERO);
                RubiksCubeContraptionEntity contraptionEntity = RubiksCubeContraptionEntity.create(self().getLevel(),
                        contraption, pivot.getCenter(), getFrontFacing(), pos, this);
                contraptionEntity.setPos(pivot.getX(), pivot.getY(), pivot.getZ());
                this.self().getLevel().addFreshEntity(contraptionEntity);
                ce.put(group, contraptionEntity);
            }
            return ce;
        }
        return null;
    }

    @Override
    public BlockPos getAssemblyPivot() {
        return MachineUtils.getOffset(this, 0, 0, 1);
    }
}
