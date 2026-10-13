package com.mo_guang.ctpp.common.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.mo_guang.ctpp.CTPP;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

@PrefixGameTestTemplate(false)
@GameTestHolder(CTPP.MODID)
public class KineticVisualOwnershipTest {

    @GameTest(templateNamespace = CTPP.MODID, template = "empty", batch = "KineticVisualOwnership")
    public static void exactOwnersReleaseIndependently(GameTestHelper helper) {
        KineticBlockEntity shaft = placeShaft(helper);
        IKineticBlockEntityExtension extension = (IKineticBlockEntityExtension) shaft;
        BlockPos ownerPos = helper.absolutePos(new BlockPos(2, 1, 0));

        extension.ctpp$claimMultiblockOwner(ownerPos, 0, 64.0F);
        extension.ctpp$claimMultiblockOwner(ownerPos, -1, 64.0F);
        helper.assertTrue(owners(shaft).isEmpty(), "An invalid ownership epoch claimed the shaft");

        extension.ctpp$claimMultiblockOwner(ownerPos, 1, 32.0F);
        extension.ctpp$claimMultiblockOwner(ownerPos, 1, 32.0F);
        helper.assertTrue(owners(shaft).size() == 1, "Repeated claims duplicated the same owner");
        helper.assertFalse(shaft.updateSpeed, "A claimed shaft still requested Create propagation");

        // A newer controller at the same position is a distinct owner, not a position-only replacement.
        extension.ctpp$claimMultiblockOwner(ownerPos, 2, 64.0F);
        helper.assertTrue(owners(shaft).size() == 2, "Distinct ownership epochs were merged");
        extension.ctpp$releaseMultiblockOwner(ownerPos, 1);
        ListTag remaining = owners(shaft);
        helper.assertTrue(remaining.size() == 1, "Releasing one owner removed the other owner");
        helper.assertTrue(MultiblockOwner.load(remaining.getCompound(0)).equals(new MultiblockOwner(ownerPos, 2)),
                "Releasing an old epoch removed the newer epoch");
        helper.assertTrue(remaining.getCompound(0).getFloat("VisualSpeed") == 64.0F,
                "Releasing an old epoch changed the remaining owner's visual request");
        helper.assertFalse(shaft.updateSpeed, "Create propagation resumed while another owner remained");

        extension.ctpp$releaseMultiblockOwner(ownerPos, 1);
        helper.assertTrue(owners(shaft).size() == 1, "Repeated release removed an unrelated owner");
        extension.ctpp$releaseMultiblockOwner(ownerPos, 2);
        helper.assertTrue(owners(shaft).isEmpty(), "The final owner was not released");
        helper.assertTrue(shaft.updateSpeed, "The final release did not resume Create propagation");
        helper.succeed();
    }

    @GameTest(templateNamespace = CTPP.MODID, template = "empty", batch = "KineticVisualOwnership")
    public static void diskLoadPreservesOwnersWithoutRestoringVisualRequests(GameTestHelper helper) {
        KineticBlockEntity original = placeShaft(helper);
        IKineticBlockEntityExtension extension = (IKineticBlockEntityExtension) original;
        MultiblockOwner first = new MultiblockOwner(helper.absolutePos(new BlockPos(2, 1, 0)), 7);
        MultiblockOwner second = new MultiblockOwner(helper.absolutePos(new BlockPos(3, 1, 0)), 9);
        extension.ctpp$claimMultiblockOwner(first.controllerPos(), first.instanceId(), 32.0F);
        extension.ctpp$claimMultiblockOwner(second.controllerPos(), second.instanceId(), -64.0F);
        CompoundTag saved = original.saveWithoutMetadata();
        ListTag savedOwners = saved.getList("CTPPMultiblockOwners", Tag.TAG_COMPOUND);
        helper.assertTrue(savedOwners.size() == 2, "The fixture did not persist both owners");
        for (int i = 0; i < savedOwners.size(); i++) {
            helper.assertTrue(savedOwners.getCompound(i).getFloat("VisualSpeed") != 0.0F,
                    "The fixture did not persist its active visual request");
        }

        KineticBlockEntity restored = (KineticBlockEntity) original.getType().create(
                original.getBlockPos(), original.getBlockState());
        helper.assertTrue(restored != null, "The shaft block entity could not be recreated");
        restored.load(saved);
        ListTag restoredOwners = owners(restored);
        helper.assertTrue(restoredOwners.size() == 2, "Disk loading discarded persisted owners");
        boolean foundFirst = false;
        boolean foundSecond = false;
        for (int i = 0; i < restoredOwners.size(); i++) {
            CompoundTag ownerTag = restoredOwners.getCompound(i);
            MultiblockOwner owner = MultiblockOwner.load(ownerTag);
            foundFirst |= owner.equals(first);
            foundSecond |= owner.equals(second);
            helper.assertTrue(ownerTag.getFloat("VisualSpeed") == 0.0F,
                    "Disk loading restored a visual request before controller revalidation");
        }
        helper.assertTrue(foundFirst && foundSecond, "Disk loading changed an owner's position or epoch");
        helper.succeed();
    }

    private static KineticBlockEntity placeShaft(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, AllBlocks.SHAFT.get());
        return (KineticBlockEntity) helper.getBlockEntity(pos);
    }

    private static ListTag owners(KineticBlockEntity shaft) {
        return shaft.saveWithoutMetadata().getList("CTPPMultiblockOwners", Tag.TAG_COMPOUND);
    }
}
