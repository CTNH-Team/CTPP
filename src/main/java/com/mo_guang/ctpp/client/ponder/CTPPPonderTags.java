package com.mo_guang.ctpp.client.ponder;

import com.gregtechceu.gtceu.api.GTValues;

import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

import com.mo_guang.ctpp.CTPP;
import com.mo_guang.ctpp.registry.CTPPMachines;
import com.mo_guang.ctpp.registry.CTPPMultiblockMachines;
import com.simibubi.create.AllBlocks;
import tech.vixhentx.mcmod.ctnhlib.client.ponder.CTNHPonderTagHelper;

import static com.mo_guang.ctpp.CTPPRegistration.REGISTRATE;

public final class CTPPPonderTags {

    public static final ResourceLocation CTPPPonder = ResourceLocation.tryBuild(CTPP.MODID, "kinetic");
    public static final ResourceLocation KineticHatch = ResourceLocation.tryBuild(CTPP.MODID, "kinetic_hatch");

    private CTPPPonderTags() {}

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        CTNHPonderTagHelper.registerTag(REGISTRATE, helper, CTPPPonder,
                "CTPP Kinetic Machine", "CTPP机器",
                "Ponders on CTPP kinetic machines", "CTPP机器思索")
                .addToIndex()
                .item(AllBlocks.COGWHEEL.asItem(), true, false)
                .register();

        CTNHPonderTagHelper.registerTag(REGISTRATE, helper, KineticHatch,
                "Kinetic Hatch", "应力仓",
                "Ponders on CTPP kinetic input and output hatches", "CTPP应力输入仓与输出仓思索")
                .addToIndex()
                .item(CTPPMachines.KINETIC_INPUT_BOX[GTValues.HV].getItem(), true, false)
                .register();

        helper.addToTag(CTPPPonder)
                .add(CTPPMultiblockMachines.BIG_DAM.getId())
                .add(CTPPMultiblockMachines.KINETIC_GENERATOR.getId())
                .add(CTPPMultiblockMachines.SMASHING_FACTORY.getId())
                .add(CTPPMultiblockMachines.WINDMILL_CONTROL_CENTER.getId())
                .add(CTPPMachines.CARBON_BRUSHES.getId());

        var hatchTag = helper.addToTag(KineticHatch);
        for (int tier : GTValues.ALL_TIERS) {
            hatchTag.add(CTPPMachines.KINETIC_INPUT_BOX[tier].getId());
            hatchTag.add(CTPPMachines.KINETIC_OUTPUT_BOX[tier].getId());
        }

        CTPP.LOGGER.info("Ponder tags initialized");
    }
}
