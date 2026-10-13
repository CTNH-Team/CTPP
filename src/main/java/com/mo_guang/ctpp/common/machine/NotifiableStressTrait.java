package com.mo_guang.ctpp.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.ICapabilityTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.util.Mth;

import com.mo_guang.ctpp.api.StressRecipeCapability;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class NotifiableStressTrait extends NotifiableRecipeHandlerTrait<Float> implements ICapabilityTrait {

    @Getter
    @Setter
    private long timeStamp;
    @Getter
    public final IO handlerIO;
    @Getter
    public final IO capabilityIO;
    private float available, lastSpeed;

    public NotifiableStressTrait(MetaMachine machine, IO handlerIO, IO capabilityIO) {
        super(machine);
        this.handlerIO = handlerIO;
        this.capabilityIO = capabilityIO;
        this.lastSpeed = 0;
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        if (machine instanceof IKineticMachine kineticMachine) {
            machine.subscribeServerTick(() -> {
                var speed = kineticMachine.getKineticHolder().getSpeed();
                if (speed != lastSpeed) {
                    lastSpeed = speed;
                    notifyListeners();
                }
            });
        }
    }

    @Override
    public boolean handleRecipe(IO io, GTRecipe recipe, List<Float> left,
                                boolean simulate) {
        if (!(machine instanceof IKineticMachine kineticMachine) || !handlerIO.support(io)) {
            return false;
        }

        var kineticDefinition = kineticMachine.getKineticDefinition();
        for (var it = left.listIterator(); it.hasNext();) {
            float stress = it.next();
            if (stress <= 0) {
                it.remove();
                continue;
            }

            float handled = 0;
            if (io == IO.IN && !kineticDefinition.isSource()) {
                float capacity = Mth.abs(kineticMachine.getKineticHolder().getSpeed()) * kineticDefinition.torque;
                handled = Math.min(stress, capacity);
            } else if (io == IO.OUT && kineticDefinition.isSource()) {
                handled = kineticMachine.getKineticHolder().scheduleWorking(stress, simulate);
            }

            if (!simulate) {
                available = handled;
            }

            stress -= handled;
            // Stress outputs are voidable, but GTM only discards excess after a real output pass.
            // Treat the remaining stress as handled during simulation so a partial output can start the recipe.
            if (simulate && io == IO.OUT && kineticDefinition.isSource()) {
                it.remove();
                continue;
            }
            if (stress <= 0.0001f) {
                it.remove();
            } else {
                it.set(stress);
            }
        }

        return left.isEmpty();
    }

    @Override
    public List<Object> getContents() {
        return List.of(getStressAvailable());
    }

    @Override
    public double getTotalContentAmount() {
        return getStressAvailable();
    }

    public float getStressAvailable() {
        if (machine instanceof IKineticMachine kineticMachine) {
            var kineticDefinition = kineticMachine.getKineticDefinition();
            if (handlerIO.support(IO.IN) && !kineticDefinition.isSource()) {
                return Mth.abs(kineticMachine.getKineticHolder().getSpeed()) * kineticDefinition.torque;
            }
            if (handlerIO.support(IO.OUT) && kineticDefinition.isSource()) {
                return available;
            }
        }
        return available;
    }

    public void stopWorking() {
        available = 0;
        if (machine instanceof IKineticMachine kineticMachine) {
            var kineticDefinition = kineticMachine.getKineticDefinition();
            if (kineticDefinition.isSource()) {
                kineticMachine.getKineticHolder().stopWorking();
            }
        }
    }

    @Override
    public RecipeCapability<Float> getCapability() {
        return StressRecipeCapability.CAP;
    }
}
