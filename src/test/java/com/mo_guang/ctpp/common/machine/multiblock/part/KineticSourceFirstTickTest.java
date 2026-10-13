package com.mo_guang.ctpp.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.mo_guang.ctpp.CTPP;
import com.mo_guang.ctpp.common.blockentity.KineticMachineBlockEntity;
import com.mo_guang.ctpp.registry.CTPPMachines;
import com.simibubi.create.content.kinetics.KineticNetwork;

@PrefixGameTestTemplate(false)
@GameTestHolder(CTPP.MODID)
public class KineticSourceFirstTickTest {

    private static final float EPSILON = 0.0001F;

    @GameTest(templateNamespace = CTPP.MODID, template = "empty", batch = "KineticSourcePersistence")
    public static void firstTickConsumesOldSourceBeforeApplyingDesiredSpeed(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(0, 1, 0);
        BlockPos pos = helper.absolutePos(relativePos);
        helper.setBlock(relativePos, CTPPMachines.KINETIC_OUTPUT_BOX[GTValues.LV].getBlock());
        KineticMachineBlockEntity original = (KineticMachineBlockEntity) helper.getBlockEntity(relativePos);

        // Seed the current persisted state directly; controller scheduling is covered by the ownership test.
        original.workingSpeed = 48.0F;
        original.updateGeneratedRotation();
        KineticNetwork restoredNetwork = original.getOrCreateNetwork();
        float loadedSourceCapacity = restoredNetwork.calculateCapacity();
        float loadedSourceStress = restoredNetwork.calculateStress();

        // Capture a valid crash/restart boundary: Create still has the applied 48 rpm source identity, while GT has
        // already requested 64 rpm for the next holder reconciliation.
        original.workingSpeed = 64.0F;
        CompoundTag saved = original.saveWithFullMetadata();
        assertFloat(helper, saved.getFloat("workingSpeed"), 64.0F,
                "The fixture did not save the newer desired speed");
        assertFloat(helper, saved.getFloat("appliedGeneratedSpeed"), 48.0F,
                "The fixture did not save the old applied source identity");

        float unloadedCapacity = 640.0F;
        float unloadedStress = 96.0F;
        CompoundTag networkTag = saved.getCompound("Network");
        helper.assertTrue(networkTag.contains("AddedCapacity", Tag.TAG_FLOAT),
                "The fixture did not save its individual source contribution");
        networkTag.putFloat("Capacity", loadedSourceCapacity + unloadedCapacity);
        networkTag.putFloat("Stress", loadedSourceStress + unloadedStress);
        networkTag.putInt("Size", 2);
        KineticSourcePersistenceTest.addUnavailableOwner(helper, saved);

        // Preserve the network object exactly as a chunk unload would, but replace the holder with a fresh Java
        // instance so SmartBlockEntity's first tick invokes initialize() through the production order.
        original.onChunkUnloaded();
        helper.getLevel().removeBlockEntity(pos);
        restoredNetwork.sources.clear();
        restoredNetwork.members.clear();
        restoredNetwork.initialized = false;

        ObservingKineticMachineBlockEntity restored = new ObservingKineticMachineBlockEntity(
                original.getType(), pos, original.getBlockState());
        restored.load(saved);
        helper.getLevel().setBlockEntity(restored);
        // Forge queues onLoad until the next world tick. Observe that real path, including the machine tick before
        // the holder tick, instead of manually invoking either lifecycle method out of order.
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(restored.onLoadCalled,
                    "Installing the restored holder did not invoke its production onLoad path");

            helper.assertTrue(restored.initializeCalls == 1,
                    "The first holder tick did not initialize the restored Create network exactly once");
            assertFloat(helper, restored.generatedSpeedAtInitialize, 48.0F,
                    "initialize() did not observe the old applied source identity");
            assertFloat(helper, restored.workingSpeed, 64.0F,
                    "The first holder tick discarded the newer desired speed");
            assertFloat(helper, restored.getAppliedGeneratedSpeed(), 64.0F,
                    "The first holder tick did not reconcile to the newer desired speed");
            helper.assertTrue(restored.getOrCreateNetwork() == restoredNetwork,
                    "The first holder tick replaced the persisted network object");
            assertFloat(helper, restoredNetwork.calculateCapacity(),
                    unloadedCapacity + loadedSourceCapacity * 64.0F / 48.0F,
                    "The first holder tick lost or double-counted unloaded capacity");
            assertFloat(helper, restoredNetwork.calculateStress(),
                    unloadedStress + loadedSourceStress * 64.0F / 48.0F,
                    "The first holder tick lost or double-counted unloaded stress");
            helper.assertTrue(restoredNetwork.getSize() == 2,
                    "The first holder tick lost the still-unloaded member count");
            helper.succeed();
        });
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
