package com.mo_guang.ctpp.common.machine.multiblock;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.network.chat.Component;

import com.ctnhlang.CN;
import com.ctnhlang.EN;
import com.mo_guang.ctpp.common.machine.IKineticMachine;
import com.mo_guang.ctpp.common.machine.multiblock.part.KineticPartMachine;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.util.List;

public class KineticWorkableMultiblockMachine extends KineticMultiblockMachine implements ITieredMachine {

    @CN("输入应力：%dsu")
    @EN("Input stress: %dsu")
    static Lang inputStress;

    @CN("并行数： %d")
    @EN("Parallelism: %d")
    static Lang parallel;

    @CN("必须输入相同转速")
    @EN("All kinetic inputs must run at the same rotation speed")
    static Lang sameSpeedRequired;

    @Getter
    public float maxTorque = 0;
    public boolean speedConsistent = false;

    public KineticWorkableMultiblockMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        refreshInputSpeed();
    }

    @Override
    public int getTier() {
        return GTUtil.getTierByVoltage((long) (maxTorque / 4)) + speed >= 128 ? 1 : 0;
    }

    public float getTotalInputStress() {
        float input = 0;
        for (IMultiPart part : getParts()) {
            if (part instanceof KineticPartMachine kineticPart && kineticPart.getIO() == IO.IN) {
                input += Math.abs(kineticPart.getKineticHolder().getSpeed()) *
                        kineticPart.getKineticDefinition().torque;
            }
        }
        return input;
    }

    public boolean checkInputSpeedConsistent() {
        float firstSpeed = Float.NaN;
        for (IMultiPart part : getParts()) {
            if (part instanceof KineticPartMachine kineticPart && kineticPart.getIO() == IO.IN) {
                float partSpeed = Math.abs(kineticPart.getKineticHolder().getSpeed());
                if (partSpeed < 0.01F) continue;
                if (Float.isNaN(firstSpeed)) {
                    firstSpeed = partSpeed;
                } else if (Math.abs(partSpeed - firstSpeed) > 0.01F) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public @Nullable Component beforeWorking(@NotNull GTRecipe recipe) {
        if (!speedConsistent) {
            return sameSpeedRequired.translate();
        }
        Component result = super.beforeWorking(recipe);
        return result;
    }

    @Override
    public boolean onWorking() {
        return speedConsistent && super.onWorking();
    }

    @Override
    public void onChanged() {
        super.onChanged();
        refreshInputSpeed();
    }

    private void refreshInputSpeed() {
        speedConsistent = checkInputSpeedConsistent();
        if (speedConsistent) {
            updateMachineSpeed();
        } else if (speed != 0) {
            speed = 0;
            updateRotateBlocks(true);
        }
    }

    public void updateMachineSpeed() {
        var previousSpeed = speed;
        speed = 0;
        for (IMultiPart part : getParts()) {
            if (part instanceof IKineticMachine kineticPart && kineticPart.getKineticHolder().getSpeed() != 0) {
                speed = kineticPart.getKineticHolder().getSpeed();
                if (speed != previousSpeed) {
                    updateRotateBlocks(true);
                }
                return;
            }
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (isStructureOperational()) {
            textList.add(inputStress.translate(getTotalInputStress()));
            var lastRecipe = getRecipeLogic().getLastRecipe();
            if (lastRecipe != null)
                textList.add(parallel.translate(lastRecipe.parallels));
        }
    }
}
