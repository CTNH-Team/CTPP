package com.mo_guang.ctpp.common.machine.multiblock;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.RecipeMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.ctnhlang.CN;
import com.ctnhlang.EN;
import com.mo_guang.ctpp.api.StressRecipeCapability;
import com.mo_guang.ctpp.common.blockentity.IKineticBlockEntityExtension;
import com.mo_guang.ctpp.common.blockentity.KineticMachineBlockEntity;
import com.mo_guang.ctpp.common.machine.NotifiableStressTrait;
import com.mo_guang.ctpp.common.machine.multiblock.part.MechanicalUpgradePartMachine;
import com.mo_guang.ctpp.dynamicPart.rotation.IContraptionMultiblock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import lombok.Getter;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

public abstract class KineticMultiblockMachine extends RecipeMultiblockMachine
                                               implements IFancyUIMachine, IDisplayUIMachine,
                                               IMultiblockKineticOwner {

    @Getter
    public LongSet rotateBlocks;
    @Getter
    public LongSet blazeBlocks;

    @Getter
    public float speed = 0;
    public int tier = 0;

    @CN("暂停中：")
    @EN("Waiting：")
    static Lang waiting;

    public KineticMultiblockMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public void onStructureFormed() {
        LongSet oldRotateBlocks = rotateBlocks;
        super.onStructureFormed();
        checkTier();
        LongSet newRotateBlocks = getMultiblockState().getMatchContext().getOrDefault("roBlocks",
                LongSets.emptySet());
        LongSet newBlazeBlocks = getMultiblockState().getMatchContext().getOrDefault("bbBlocks",
                LongSets.emptySet());
        releaseRotateBlocks(oldRotateBlocks, newRotateBlocks);
        rotateBlocks = newRotateBlocks;
        blazeBlocks = newBlazeBlocks;
        updateActiveBlocks(getRecipeLogic().isWorking());
    }

    @Override
    public void onStructureInvalid() {
        stopWorking();
        super.onStructureInvalid();
        releaseRotateBlocks(rotateBlocks, null);
        rotateBlocks = null;
        blazeBlocks = null;
    }

    @Override
    protected void onStructureRevalidationChanged(boolean pending) {
        super.onStructureRevalidationChanged(pending);
        if (pending) {
            // Keep the exact claims across a chunk unload, but withdraw only this controller's transient outputs.
            updateActiveBlocks(false);
        }
    }

    @Override
    public boolean shouldIgnoreChange(BlockPos pos, BlockState state) {
        if (this instanceof IContraptionMultiblock<?> contraptionMultiblock &&
                contraptionMultiblock.shouldIgnoreContraptionChange(pos, state)) {
            return true;
        }
        if (!state.getBlock().equals(Blocks.AIR)) {
            long posLong = pos.asLong();
            if ((blazeBlocks != null && blazeBlocks.contains(posLong)) ||
                    (rotateBlocks != null && rotateBlocks.contains(posLong))) {
                return true;
            }
        }
        return super.shouldIgnoreChange(pos, state);
    }

    public void onTierChanged() {}

    public void stopWorking() {
        getCapabilitiesFlat(IO.OUT, StressRecipeCapability.CAP).forEach(iRecipeHandler -> {
            if (iRecipeHandler instanceof NotifiableStressTrait notifiableStressTrait) {
                notifiableStressTrait.stopWorking();
            }
        });
    }

    @Override
    public void notifyWorkStatusChanged(WorkLogic.Status oldStatus,
                                        WorkLogic.Status newStatus) {
        super.notifyWorkStatusChanged(oldStatus, newStatus);
        if (newStatus != WorkLogic.Status.WORKING) stopWorking();
    }

    @Override
    public void updateActiveBlocks(boolean active) {
        boolean operationalActive = active && isStructureOperational();
        super.updateActiveBlocks(operationalActive);
        updateRotateBlocks(operationalActive);
        try {
            updateBlazeBlocks(operationalActive);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void checkTier() {
        tier = 0;
        for (IMultiPart multiPart : getParts()) {
            if (multiPart instanceof MechanicalUpgradePartMachine upgradePartMachine) {
                tier = Math.max(upgradePartMachine.getMechanicalTier(), tier);
            }
        }
    }

    public void updateRotateBlocks(boolean active) {
        if (rotateBlocks == null || getLevel() == null) {
            return;
        }
        for (long pos : rotateBlocks) {
            var blockPos = BlockPos.of(pos);
            if (!getLevel().isLoaded(blockPos)) continue;
            updateRotateBlock(active, getLevel().getBlockEntity(blockPos));
        }
    }

    public void updateRotateBlock(boolean active, BlockEntity blockEntity) {
        if (blockEntity instanceof KineticBlockEntity kineticBlockEntity &&
                kineticBlockEntity instanceof IKineticBlockEntityExtension extension &&
                !(kineticBlockEntity instanceof KineticMachineBlockEntity)) {
            extension.ctpp$claimMultiblockOwner(getPos(), getStructureInstanceId(), active ? speed : 0.0F);
        }
    }

    public void updateBlazeBlocks(boolean active) throws NoSuchMethodException, InvocationTargetException,
                                                  IllegalAccessException {
        if (blazeBlocks == null || getLevel() == null) {
            return;
        }
        BlazeBurnerBlock.HeatLevel heat = getRequestedBlazeHeat(active);
        for (long pos : blazeBlocks) {
            var blockPos = BlockPos.of(pos);
            if (!getLevel().isLoaded(blockPos)) continue;
            if (getLevel().getBlockEntity(blockPos) instanceof BlazeBurnerBlockEntity blazeBurner) {
                Method method = BlazeBurnerBlockEntity.class.getDeclaredMethod("setBlockHeat",
                        BlazeBurnerBlock.HeatLevel.class);
                method.setAccessible(true);
                method.invoke(blazeBurner, heat);
            }
        }
    }

    private BlazeBurnerBlock.HeatLevel getRequestedBlazeHeat(boolean active) {
        if (!active) return BlazeBurnerBlock.HeatLevel.SMOULDERING;
        if (speed >= 256) return BlazeBurnerBlock.HeatLevel.SEETHING;
        if (speed >= 128) return BlazeBurnerBlock.HeatLevel.KINDLED;
        return BlazeBurnerBlock.HeatLevel.FADING;
    }

    private void releaseRotateBlocks(LongSet oldBlocks, LongSet retainedBlocks) {
        if (oldBlocks == null || getLevel() == null) return;
        for (long pos : oldBlocks) {
            if (retainedBlocks != null && retainedBlocks.contains(pos)) continue;
            BlockPos blockPos = BlockPos.of(pos);
            if (!getLevel().isLoaded(blockPos)) continue;
            if (getLevel().getBlockEntity(blockPos) instanceof KineticBlockEntity kineticBlockEntity &&
                    kineticBlockEntity instanceof IKineticBlockEntityExtension extension) {
                extension.ctpp$releaseMultiblockOwner(getPos(), getStructureInstanceId());
            }
        }
    }

    @Override
    public boolean ownsKineticVisual(BlockPos pos) {
        return rotateBlocks != null && rotateBlocks.contains(pos.asLong());
    }

    @Override
    public float getKineticVisualSpeed(BlockPos pos) {
        return ownsKineticVisual(pos) && isStructureOperational() && getRecipeLogic().isWorking() ? speed : 0.0F;
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public void addDisplayText(List<Component> textList) {
        int numParallels;
        int subtickParallels;
        int batchParallels;
        int totalRuns;
        boolean exact = false;
        if (getRecipeLogic().isActive() && getRecipeLogic().getLastRecipe() != null) {
            numParallels = getRecipeLogic().getLastRecipe().parallels;
            subtickParallels = getRecipeLogic().getLastRecipe().subtickParallels;
            batchParallels = getRecipeLogic().getLastRecipe().batchParallels;
            totalRuns = getRecipeLogic().getLastRecipe().getTotalRuns();
            exact = true;
        } else {
            numParallels = getParallelHatch()
                    .map(IParallelHatch::getCurrentParallel)
                    .orElse(0);
            subtickParallels = 0;
            batchParallels = 0;
            totalRuns = 0;
        }
        if (getRecipeLogic().isWaiting()) {
            textList.add(waiting.translate()
                    .withStyle(ChatFormatting.RED));
            for (var reason : getRecipeLogic().getFancyTooltip()) {
                textList.add(Component.literal(" - " + reason.getString()));
            }
        }
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(getRecipeLogic().isWorkingEnabled(), getRecipeLogic().isActive())
                .addMachineModeLine(getRecipeType(), getRecipeTypes().length > 1)
                .addTotalRunsLine(totalRuns)
                .addParallelsLine(numParallels, exact)
                .addSubtickParallelsLine(subtickParallels)
                .addBatchModeLine(isBatchEnabled(), batchParallels)
                .addWorkingStatusLine()
                .addProgressLine(getRecipeLogic().getProgress(), getRecipeLogic().getMaxProgress(),
                        getRecipeLogic().getProgressPercent())
                .addRecipeFailReasonLine(recipeLogic)
                .addOutputLines(getRecipeLogic().getLastRecipe());
        getDefinition().getAdditionalDisplay().accept(this, textList);
        IDisplayUIMachine.super.addDisplayText(textList);
    }

    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 182 + 8, 117 + 8);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117).setBackground(getScreenTexture())
                .addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText)
                        .textSupplier(this.getLevel().isClientSide ? null : this::addDisplayText)
                        .setMaxWidthLimit(200)
                        .clickHandler(this::handleDisplayClick)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(198, 208, this, entityPlayer).widget(new FancyMachineUIWidget(this, 198, 208));
    }

    @Override
    public List<IFancyUIProvider> getSubTabs() {
        return getParts().stream().filter(Objects::nonNull).map(IFancyUIProvider.class::cast).toList();
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        for (IMultiPart part : getParts()) {
            part.attachFancyTooltipsToController(this, tooltipsPanel);
        }
    }

    public class KineticRecipeLogic extends RecipeLogic {

        public KineticRecipeLogic(IRecipeLogicMachine machine) {
            super(machine);
        }
    }
}
