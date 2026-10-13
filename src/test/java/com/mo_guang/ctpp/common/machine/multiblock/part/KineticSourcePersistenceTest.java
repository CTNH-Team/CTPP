package com.mo_guang.ctpp.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.pattern.Predicates;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.mo_guang.ctpp.CTPP;
import com.mo_guang.ctpp.common.blockentity.KineticMachineBlockEntity;
import com.mo_guang.ctpp.common.machine.multiblock.KineticOutputMachine;
import com.mo_guang.ctpp.registry.CTPPMachines;
import com.mo_guang.ctpp.registry.CTPPMultiblockMachines;
import com.simibubi.create.content.kinetics.KineticNetwork;

import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestHolder(CTPP.MODID)
public class KineticSourcePersistenceTest {

    private static final String TEMPLATE_NAMESPACE = CTPP.MODID;
    private static final String TEMPLATE = "empty";
    private static final String BATCH = "KineticSourcePersistence";
    private static final float EPSILON = 0.0001F;

    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = TEMPLATE, batch = BATCH)
    public static void stableRetuneAndSuspensionPreserveDesiredSource(GameTestHelper helper) {
        KineticMachineBlockEntity source = placeOutputBox(helper, new BlockPos(0, 1, 0));

        setDesiredAndReconcile(source, 32.0F);
        KineticNetwork originalNetwork = source.getOrCreateNetwork();
        float originalCapacity = originalNetwork.calculateCapacity();

        setDesiredAndReconcile(source, 64.0F);
        helper.assertTrue(source.getOrCreateNetwork() == originalNetwork,
                "Retuning a running independent source replaced its KineticNetwork object");
        assertFloat(helper, source.getAppliedGeneratedSpeed(), 64.0F,
                "The retuned source did not publish its requested speed");
        helper.assertTrue(originalNetwork.calculateCapacity() > originalCapacity,
                "The stable retune did not update network capacity");

        source.setGeneratedSourceSuspended(true);
        source.updateGeneratedRotation();
        assertFloat(helper, source.workingSpeed, 64.0F,
                "Suspending the source discarded the desired speed");
        assertFloat(helper, source.getAppliedGeneratedSpeed(), 0.0F,
                "Suspending the source left an applied generator speed");
        assertFloat(helper, source.getGeneratedSpeed(), 0.0F,
                "A suspended output still advertised itself as a Create source");

        source.setGeneratedSourceSuspended(false);
        source.updateGeneratedRotation();
        assertFloat(helper, source.workingSpeed, 64.0F,
                "Resuming the source changed the desired speed");
        assertFloat(helper, source.getAppliedGeneratedSpeed(), 64.0F,
                "Resuming the source did not restore the desired speed");
        helper.assertTrue(source.hasNetwork(), "The resumed source did not rejoin a Create network");
        helper.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = TEMPLATE, batch = BATCH)
    public static void restoredSourceConsumesItsSavedAggregateExactlyOnce(GameTestHelper helper) {
        KineticMachineBlockEntity source = placeOutputBox(helper, new BlockPos(0, 1, 0));
        setDesiredAndReconcile(source, 48.0F);

        KineticNetwork restoredNetwork = source.getOrCreateNetwork();
        float loadedSourceCapacity = restoredNetwork.calculateCapacity();
        float loadedSourceStress = restoredNetwork.calculateStress();
        float unloadedCapacity = 640.0F;
        float unloadedStress = 96.0F;
        CompoundTag saved = source.saveWithFullMetadata();
        CompoundTag networkTag = saved.getCompound("Network");
        helper.assertTrue(networkTag.contains("AddedCapacity", Tag.TAG_FLOAT),
                "The source fixture did not save its individual capacity contribution");
        networkTag.putFloat("Capacity", loadedSourceCapacity + unloadedCapacity);
        networkTag.putFloat("Stress", loadedSourceStress + unloadedStress);
        networkTag.putInt("Size", 2);
        addUnavailableOwner(helper, saved);

        // Loading NBT into the already initialized fixture would retain Create's wasMoved flag and deliberately skip
        // kinetic data. A disk reload always creates a fresh holder, so exercise that real lifecycle instead.
        ObservingKineticMachineBlockEntity restored = restoreFreshHolder(helper, new BlockPos(0, 1, 0), source,
                restoredNetwork, saved);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(restored.onLoadCalled,
                    "The fresh aggregate holder did not run its production onLoad path");
            helper.assertTrue(restored.initializeCalls == 1,
                    "The fresh aggregate holder did not initialize exactly once on its first tick");
            assertFloat(helper, restored.generatedSpeedAtInitialize, 48.0F,
                    "initialize() did not consume the saved applied source identity");
            helper.assertTrue(restored.getOrCreateNetwork() == restoredNetwork,
                    "Loading the source replaced the persisted KineticNetwork identity");
            assertFloat(helper, restoredNetwork.calculateCapacity(), loadedSourceCapacity + unloadedCapacity,
                    "initialize/addSilently double-counted or lost the saved source contribution");
            assertFloat(helper, restoredNetwork.calculateStress(), loadedSourceStress + unloadedStress,
                    "initialize/addSilently double-counted or lost saved network stress");
            helper.assertTrue(restoredNetwork.getSize() == 2,
                    "The restored source did not consume exactly one unloaded member slot");

            restored.initialize();
            assertFloat(helper, restoredNetwork.calculateCapacity(), loadedSourceCapacity + unloadedCapacity,
                    "A repeated initialize call consumed or added the source contribution twice");
            assertFloat(helper, restoredNetwork.calculateStress(), loadedSourceStress + unloadedStress,
                    "A repeated initialize call consumed or added source stress twice");
            helper.assertTrue(restoredNetwork.getSize() == 2,
                    "A repeated initialize call changed the restored network size");

            setDesiredAndReconcile(restored, 64.0F);
            helper.assertTrue(restored.getOrCreateNetwork() == restoredNetwork,
                    "Stable retuning replaced the restored network containing an unloaded member");
            assertFloat(helper, restoredNetwork.calculateCapacity(),
                    unloadedCapacity + loadedSourceCapacity * 64.0F / 48.0F,
                    "Stable retuning lost or modified the unloaded capacity balance");
            assertFloat(helper, restoredNetwork.calculateStress(),
                    unloadedStress + loadedSourceStress * 64.0F / 48.0F,
                    "Stable retuning lost or modified the unloaded stress balance");
            helper.assertTrue(restoredNetwork.getSize() == 2,
                    "Stable retuning lost the still-unloaded member count");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = TEMPLATE, batch = BATCH)
    public static void unavailableExactOwnerSuspendsAfterBoundedReloadGrace(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(0, 1, 0);
        KineticMachineBlockEntity source = placeOutputBox(helper, relativePos);
        setDesiredAndReconcile(source, 48.0F);
        CompoundTag saved = source.saveWithFullMetadata();
        addUnavailableOwner(helper, saved);
        ObservingKineticMachineBlockEntity restored = restoreFreshHolder(helper, relativePos, source,
                source.getOrCreateNetwork(), saved);
        helper.runAfterDelay(1, () -> {
            assertFloat(helper, restored.getAppliedGeneratedSpeed(), 48.0F,
                    "An unavailable exact owner did not receive the initial reload grace");
            helper.assertFalse(restored.isGeneratedSourceSuspended(),
                    "The source was suspended before its reload grace elapsed");
        });
        helper.runAfterDelay(45, () -> {
            assertFloat(helper, restored.getAppliedGeneratedSpeed(), 0.0F,
                    "The unavailable owner kept publishing stress after its bounded grace");
            assertFloat(helper, restored.workingSpeed, 48.0F,
                    "An unavailable owner lost its persisted desired output");
            helper.assertTrue(restored.isGeneratedSourceSuspended(),
                    "The unavailable source did not persist its suspended state");
            KineticPartMachine part = (KineticPartMachine) restored.getMetaMachine();
            helper.assertTrue(part.getControllerBindingInstanceId(
                    NbtUtils.readBlockPos(saved.getCompound("lastControllerPos"))) == 1L,
                    "An unavailable owner lost its exact epoch binding");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = TEMPLATE, batch = BATCH)
    public static void retiredUnavailableOwnerStopsDuringReloadGrace(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(0, 1, 0);
        KineticMachineBlockEntity source = placeOutputBox(helper, relativePos);
        setDesiredAndReconcile(source, 48.0F);
        CompoundTag saved = source.saveWithFullMetadata();
        addUnavailableOwner(helper, saved);
        ObservingKineticMachineBlockEntity restored = restoreFreshHolder(helper, relativePos, source,
                source.getOrCreateNetwork(), saved);
        helper.runAfterDelay(5, () -> {
            KineticPartMachine part = (KineticPartMachine) restored.getMetaMachine();
            BlockPos ownerPos = NbtUtils.readBlockPos(saved.getCompound("lastControllerPos"));
            var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
            helper.assertFalse(savedData.isControllerPositionLoadedNoChunkRequest(ownerPos),
                    "The fixture owner became observable before retirement");
            CompoundTag beforeRetirement = new CompoundTag();
            restored.saveManagedPersistentData(beforeRetirement, false);
            helper.assertTrue(beforeRetirement.getLong("bindingReloadGraceDeadline") > helper.getLevel().getGameTime(),
                    "The fixture's reload grace expired before retirement");
            assertFloat(helper, restored.getAppliedGeneratedSpeed(), 48.0F,
                    "The fixture source stopped before its owner was retired");

            savedData.retireControllerBinding(ownerPos, 1L);
            // Check immediately, without waiting for the base part's periodic binding reconciliation.
            part.checkOutputBinding();
            assertFloat(helper, restored.workingSpeed, 0.0F,
                    "A retired unavailable owner retained its desired output during reload grace");
            helper.assertTrue(part.getControllerBindingInstanceId(ownerPos) == 0,
                    "A retired unavailable owner retained its exact epoch binding");
            CompoundTag afterRetirement = new CompoundTag();
            restored.saveManagedPersistentData(afterRetirement, false);
            helper.assertTrue(afterRetirement.getLong("bindingReloadGraceDeadline") == 0,
                    "Definitive retirement retained a reload grace deadline");
            helper.assertFalse(afterRetirement.contains("lastControllerPos"),
                    "Definitive retirement retained the persisted controller position");
            assertFloat(helper, restored.getAppliedGeneratedSpeed(), 48.0F,
                    "The binding check changed the applied source before holder reconciliation");

            restored.updateGeneratedRotation();
            assertFloat(helper, restored.getAppliedGeneratedSpeed(), 0.0F,
                    "Holder reconciliation did not withdraw the retired owner's source");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = TEMPLATE, batch = BATCH)
    public static void diskAndDropNbtKeepSourceStateSeparate(GameTestHelper helper) {
        KineticMachineBlockEntity source = placeOutputBox(helper, new BlockPos(0, 1, 0));
        setDesiredAndReconcile(source, 40.0F);
        source.setGeneratedSourceSuspended(true);
        source.updateGeneratedRotation();

        CompoundTag worldTag = new CompoundTag();
        source.saveManagedPersistentData(worldTag, false);
        assertFloat(helper, worldTag.getFloat("workingSpeed"), 40.0F,
                "Normal disk NBT did not retain the desired speed");
        assertFloat(helper, worldTag.getFloat("appliedGeneratedSpeed"), 0.0F,
                "Normal disk NBT did not retain the suspended applied speed");
        helper.assertTrue(worldTag.getBoolean("generatedSourceSuspended"),
                "Normal disk NBT did not retain the suspension state");

        CompoundTag dropTag = new CompoundTag();
        source.saveManagedPersistentData(dropTag, true);
        assertNoLiveSourceState(helper, dropTag);
        helper.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = TEMPLATE, batch = BATCH)
    public static void orphanedCurrentSourceStopsAfterSavedContributionIsRestored(GameTestHelper helper) {
        KineticMachineBlockEntity source = placeOutputBox(helper, new BlockPos(0, 1, 0));
        setDesiredAndReconcile(source, 48.0F);
        // A save may happen after ownership is cleared and before the holder reconciles the old applied source.
        source.stopWorking();
        CompoundTag saved = source.saveWithFullMetadata();

        KineticNetwork restoredNetwork = source.getOrCreateNetwork();
        ObservingKineticMachineBlockEntity restored = restoreFreshHolder(helper, new BlockPos(0, 1, 0), source,
                restoredNetwork, saved);
        assertFloat(helper, restored.workingSpeed, 0.0F,
                "The orphan fixture did not restore its zero desired speed");
        assertFloat(helper, restored.getAppliedGeneratedSpeed(), 48.0F,
                "Loading discarded the saved source identity before Create could consume it");

        helper.runAfterDelay(1, () -> {
            helper.assertTrue(restored.onLoadCalled,
                    "The fresh orphan holder did not run its production onLoad path");
            helper.assertTrue(restored.initializeCalls == 1,
                    "The fresh orphan holder did not initialize exactly once on its first tick");
            assertFloat(helper, restored.generatedSpeedAtInitialize, 48.0F,
                    "initialize() did not observe the persisted applied source identity");
            assertFloat(helper, restored.getAppliedGeneratedSpeed(), 0.0F,
                    "First-tick reconciliation did not withdraw the orphaned source");
            assertFloat(helper, restored.workingSpeed, 0.0F,
                    "First-tick reconciliation revived the orphaned output request");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = TEMPLATE, batch = BATCH)
    public static void pendingExactOwnerSuspendsAndOperationalOwnerRestores(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(0, 1, 0);
        BlockPos partPos = new BlockPos(0, 2, 0);
        helper.setBlock(controllerPos, CTPPMultiblockMachines.BOOM_OF_CREATE.getBlock());
        KineticMachineBlockEntity source = placeOutputBox(helper, partPos);
        KineticPartMachine part = (KineticPartMachine) source.getMetaMachine();
        KineticOutputMachine controller = (KineticOutputMachine) ((IMachineBlockEntity) helper
                .getBlockEntity(controllerPos)).getMetaMachine();

        var state = controller.getMultiblockState();
        state.clean();
        state.addPosCache(controller.getPos(), Predicates.any());
        state.addPosCache(part.getPos(), Predicates.any());
        state.getMatchContext().set("parts", Set.of(part));
        controller.onStructureFormed();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        helper.assertTrue(savedData.tryAddMapping(state),
                "The exact-controller fixture could not install its validated mapping");
        controller.getWorkLogic().setStatus(WorkLogic.Status.WORKING);

        source.scheduleWorking(36.0F * source.getDefinition().getTorque());
        source.updateGeneratedRotation();
        helper.assertTrue(source.isGeneratedSourceSuspended(),
                "The source became live before its exact validated binding was reconciled");
        part.checkOutputBinding();
        source.updateGeneratedRotation();
        assertFloat(helper, source.getAppliedGeneratedSpeed(), 36.0F,
                "An operational exact owner with runtime membership did not restore the source");

        CompoundTag partWorldTag = new CompoundTag();
        source.saveManagedPersistentData(partWorldTag, false);
        helper.assertTrue(partWorldTag.contains("lastControllerPos"),
                "Normal disk NBT did not retain the output's controller position");
        helper.assertTrue(partWorldTag.contains("gtceuMultiblockControllerBindings", Tag.TAG_LIST),
                "Normal disk NBT did not retain the output's exact controller epoch");
        CompoundTag partDropTag = new CompoundTag();
        source.saveManagedPersistentData(partDropTag, true);
        helper.assertFalse(partDropTag.contains("lastControllerPos"),
                "Drop NBT copied the output's controller position");
        helper.assertFalse(partDropTag.contains("gtceuMultiblockControllerBindings"),
                "Drop NBT copied the output's exact controller epoch");
        assertNoLiveSourceState(helper, partDropTag);

        controller.setStructureRevalidationPending(true);
        part.checkOutputBinding();
        source.updateGeneratedRotation();
        assertFloat(helper, source.workingSpeed, 36.0F,
                "A pending exact owner discarded the desired output request");
        assertFloat(helper, source.getAppliedGeneratedSpeed(), 0.0F,
                "A known pending exact owner received the unavailable-owner grace period");

        controller.setStructureRevalidationPending(false);
        part.checkOutputBinding();
        source.updateGeneratedRotation();
        assertFloat(helper, source.getAppliedGeneratedSpeed(), 36.0F,
                "The exact operational owner did not resume its suspended source");

        long retiredInstance = controller.getStructureInstanceId();
        savedData.retireController(controller);
        part.checkOutputBinding();
        source.updateGeneratedRotation();
        assertFloat(helper, source.getAppliedGeneratedSpeed(), 0.0F,
                "A retired exact owner remained an applied source");

        part.reconcileControllerBindings(savedData);
        source.updateGeneratedRotation();
        helper.assertTrue(part.getControllerBindingInstanceId(controller.getPos()) == 0,
                "The retired exact controller epoch remained bound to the output");
        assertFloat(helper, source.workingSpeed, 0.0F,
                "Definitive controller retirement did not clear the obsolete desired request");
        helper.assertTrue(savedData.isControllerBindingRetired(controller.getPos(), retiredInstance),
                "The controller retirement tombstone was lost during output reconciliation");
        helper.succeed();
    }

    private static KineticMachineBlockEntity placeOutputBox(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, CTPPMachines.KINETIC_OUTPUT_BOX[GTValues.LV].getBlock());
        return (KineticMachineBlockEntity) helper.getBlockEntity(pos);
    }

    private static ObservingKineticMachineBlockEntity restoreFreshHolder(GameTestHelper helper, BlockPos relativePos,
                                                                         KineticMachineBlockEntity original,
                                                                         KineticNetwork restoredNetwork,
                                                                         CompoundTag saved) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        original.onChunkUnloaded();
        helper.getLevel().removeBlockEntity(absolutePos);
        restoredNetwork.sources.clear();
        restoredNetwork.members.clear();
        restoredNetwork.initialized = false;

        ObservingKineticMachineBlockEntity restored = new ObservingKineticMachineBlockEntity(
                original.getType(), absolutePos, original.getBlockState());
        restored.load(saved);
        helper.getLevel().setBlockEntity(restored);
        return restored;
    }

    private static void setDesiredAndReconcile(KineticMachineBlockEntity source, float speed) {
        // Isolate Create accounting from controller recipes; the ownership test uses scheduleWorking instead.
        source.workingSpeed = speed;
        source.updateGeneratedRotation();
    }

    static void addUnavailableOwner(GameTestHelper helper, CompoundTag saved) {
        // A current-format source may outlive its controller's loaded chunk. Give the disk fixture an exact owner
        // outside the test's loaded area so the real part tick preserves it during the bounded reload grace.
        BlockPos ownerPos = helper.absolutePos(new BlockPos(1_000_000, 1, 1_000_000));
        helper.assertFalse(MultiblockWorldSavedData.getOrCreate(helper.getLevel())
                .isControllerPositionLoadedNoChunkRequest(ownerPos), "The fixture owner position is already ticking");
        saved.put("lastControllerPos", NbtUtils.writeBlockPos(ownerPos));
        CompoundTag binding = new CompoundTag();
        binding.putLong("pos", ownerPos.asLong());
        binding.putLong("instanceId", 1L);
        ListTag bindings = new ListTag();
        bindings.add(binding);
        saved.put("gtceuMultiblockControllerBindings", bindings);
    }

    private static void assertNoLiveSourceState(GameTestHelper helper, CompoundTag tag) {
        helper.assertFalse(tag.contains("workingSpeed"), "Drop NBT copied the desired source speed");
        helper.assertFalse(tag.contains("appliedGeneratedSpeed"), "Drop NBT copied the applied source speed");
        helper.assertFalse(tag.contains("generatedSourceSuspended"), "Drop NBT copied the source suspension state");
        helper.assertFalse(tag.contains("Speed"), "Drop NBT copied Create's live kinetic speed");
        helper.assertFalse(tag.contains("Source"), "Drop NBT copied Create's external source position");
        helper.assertFalse(tag.contains("Network"), "Drop NBT copied Create's live network aggregate");
    }

    private static void assertFloat(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) <= EPSILON,
                "%s (expected %s, got %s)".formatted(message, expected, actual));
    }

    private static final class ObservingKineticMachineBlockEntity extends KineticMachineBlockEntity {

        private boolean onLoadCalled;
        private int initializeCalls;
        private float generatedSpeedAtInitialize = Float.NaN;

        private ObservingKineticMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        @Override
        public void onLoad() {
            onLoadCalled = true;
            super.onLoad();
        }

        @Override
        public void initialize() {
            initializeCalls++;
            generatedSpeedAtInitialize = getGeneratedSpeed();
            super.initialize();
        }
    }
}
