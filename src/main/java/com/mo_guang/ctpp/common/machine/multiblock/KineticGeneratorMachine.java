package com.mo_guang.ctpp.common.machine.multiblock;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerGroup;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import com.ctnhlang.CN;
import com.ctnhlang.EN;
import com.mo_guang.ctpp.common.machine.multiblock.part.KineticPartMachine;
import com.mo_guang.ctpp.dynamicPart.rotation.IContraptionMultiblock;
import com.mo_guang.ctpp.dynamicPart.rotation.SimpleRotatingContraptionEntity;
import com.mo_guang.ctpp.util.MathUtil;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;
import tech.vixhentx.mcmod.ctnhlib.utils.MachineUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mo_guang.ctpp.data.recipe.KineticGeneratorRecipes.GENERATING_BOOST;

public class KineticGeneratorMachine extends KineticWorkableMultiblockMachine
                                     implements IContraptionMultiblock<SimpleRotatingContraptionEntity> {

    @Getter
    @Setter
    List<SimpleRotatingContraptionEntity> contraptionEntity = new ArrayList<>();
    private ICoilType coilType = CoilBlock.CoilType.CUPRONICKEL;
    @Persisted
    public int magnetStrength;
    @Persisted
    public int maxKineticInputTier;
    public double efficiency;
    public double outputEnergy = 0;

    public KineticGeneratorMachine(IMachineBlockEntity holder) {
        super(holder);
        magnetStrength = 0;
        efficiency = getEfficiency();
    }

    /**
     * 最高等级动力仓达到 HV 时，每高于 MV 一级直接扣去 10 个百分点的效率。
     */
    public double getTierPenalty() {
        if (maxKineticInputTier < GTValues.HV) {
            return 0;
        }
        return (maxKineticInputTier - GTValues.MV) * 0.1;
    }

    public double getEfficiency() {
        double base = (getCoilTier() * 0.1 + 0.9) * ((double) magnetStrength / (magnetStrength + 36));
        // 扣除后效率不低于 10%；磁场未成型时基础效率本就低于 10%，不因下限被抬高
        return Math.max(Math.min(base, 0.1), base - getTierPenalty());
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        var type = getMultiblockState().getMatchContext().get("CoilType");
        if (type instanceof ICoilType coil) {
            this.coilType = coil;
        }
        if (getMultiblockState().getMatchContext().get("MagnetStrength") != null) {
            this.magnetStrength = getMultiblockState().getMatchContext().get("MagnetStrength");
        }
        maxKineticInputTier = 0;
        for (IMultiPart part : getParts()) {
            if (part instanceof KineticPartMachine kineticPart && kineticPart.getIO() == IO.IN) {
                maxKineticInputTier = Math.max(maxKineticInputTier, kineticPart.getTier());
            }
        }
        efficiency = getEfficiency();
        // assemble rotating entities using interface helper
        createAndAttachRotatingEntities(MachineUtils.getOffset(this, 2, 0, 1), getContraptionRotationAxis());
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (getLevel() != null && !getLevel().isClientSide) {
            clearAndDisassembleRotatingEntities();
            magnetStrength = 0;
            maxKineticInputTier = 0;
        }
    }

    @Override
    public void updateMachineSpeed() {
        super.updateMachineSpeed();
        if (!contraptionEntity.isEmpty() && isStructureOperational()) {
            contraptionEntity.forEach(entity -> {
                var facing = getFrontFacing().getNormal();
                Vec3 newF = new Vec3(facing.getX(), facing.getY(), facing.getZ());
                entity.setRotationSpeedRPM(MathUtil.rotateByVec(newF, 90, new Vec3(0, -1, 0)), Math.min(speed, 64));
            });
        }
    }

    @Override
    public void updateRotateBlocks(boolean active) {
        super.updateRotateBlocks(active);
        if (active) {
            if (contraptionEntity != null)
                contraptionEntity.forEach(entity -> {
                    var facing = getFrontFacing().getNormal();
                    Vec3 newF = new Vec3(facing.getX(), facing.getY(), facing.getZ());
                    entity.setRotationSpeedRPM(MathUtil.rotateByVec(newF, 90, new Vec3(0, -1, 0)), Math.min(speed, 64));
                });
        }
    }

    @CN("产能功率：%d/%d EU/t (上限§4%d§r EU/t)")
    @EN("Generator Rate：%d/%d EU/t (Limit %d EU/t)")
    static Lang info0;

    @CN("线圈效率：%d%%")
    @EN("Coil Efficiency：%d%%")
    static Lang info1;

    @CN("磁场强度：%d(%d%% 能量转化率)")
    @EN("Magnet Strength：%d(%d%% Energy Conversion Rate)")
    static Lang info2;

    @CN("总效率：%d%%")
    @EN("Total Efficiency：%d%%")
    static Lang info3;

    @CN("动力仓等级惩罚：-%d%% (最高 %s)")
    @EN("Kinetic Hatch Tier Penalty：-%d%% (Highest %s)")
    static Lang info4;

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (isFormed()) {
            var voltageName = GTValues.VNF[GTUtil.getTierByVoltage((long) outputEnergy)];
            textList.add(textList.size(), info0.translate(FormattingUtil.formatNumbers(outputEnergy), voltageName,
                    this.tier > 0 ? this.tier * 4 * GTValues.V[this.tier] : 32));
            textList.add(textList.size(), info1.translate(String.format("%.1f", (getCoilTier() * 0.1 + 1) * 100))
                    .withStyle(ChatFormatting.YELLOW));
            textList.add(textList.size(),
                    info2.translate(FormattingUtil.formatNumbers(magnetStrength),
                            String.format("%.1f", ((float) magnetStrength) * 100 / (magnetStrength + 36)))
                            .withStyle(ChatFormatting.AQUA));
            if (maxKineticInputTier >= GTValues.HV) {
                textList.add(textList.size(), info4.translate(String.format("%.1f", getTierPenalty() * 100),
                        GTValues.VNF[maxKineticInputTier]).withStyle(ChatFormatting.RED));
            }
            textList.add(textList.size(), info3.translate(String.format("%.1f", getEfficiency() * 100)));
        }
    }

    public static @Nullable Component recipeModifier(MetaMachine machine, RecipeHandlerGroup group,
                                                     GTRecipe recipe) {
        if (machine instanceof KineticGeneratorMachine kmachine) {
            long limit = kmachine.tier > 0 ? kmachine.tier * 4 * GTValues.V[kmachine.tier] : 32;
            kmachine.outputEnergy = Math
                    .min(kmachine.getTotalInputStress() * kmachine.efficiency * GENERATING_BOOST / 128, limit);
            EURecipeCapability.putEUContent(recipe.tickOutputs, (long) kmachine.outputEnergy);
            return null;
        }
        return RecipeModifier.nullWrongType(KineticGeneratorMachine.class, machine);
    }

    @Override
    public boolean regressWhenWaiting() {
        return false;
    }

    public int getCoilTier() {
        return coilType.getTier();
    }

    public Map<Integer, SimpleRotatingContraptionEntity> assemble(BlockPos pivot) {
        return assembleFromPattern(pivot, getContraptionRotationAxis());
    }

    private Direction.Axis getContraptionRotationAxis() {
        return getFrontFacing().getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
    }

    @Override
    public BlockPos getAssemblyPivot() {
        return MachineUtils.getOffset(this, 2, 0, 1);
    }

    @Override
    public void onDebugAssembled() {
        updateMachineSpeed();
        updateRotateBlocks(getRecipeLogic().isWorking());
    }
}
