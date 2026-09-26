package com.mo_guang.ctpp.client.ponder;

import com.gregtechceu.gtceu.api.GTValues;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

import com.mo_guang.ctpp.CTPP;
import com.mo_guang.ctpp.client.ponder.electric.CarbonBrushes;
import com.mo_guang.ctpp.client.ponder.kinetic.BigDam;
import com.mo_guang.ctpp.client.ponder.kinetic.KineticGenerator;
import com.mo_guang.ctpp.client.ponder.kinetic.KineticHatch;
import com.mo_guang.ctpp.client.ponder.kinetic.SmashingFactory;
import com.mo_guang.ctpp.client.ponder.kinetic.WindmillControlCenter;
import com.mo_guang.ctpp.registry.CTPPBlocks;
import com.mo_guang.ctpp.registry.CTPPMachines;
import com.mo_guang.ctpp.registry.CTPPMultiblockMachines;

public final class CTPPPonderScenes {

    private CTPPPonderScenes() {}

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(CTPPMultiblockMachines.BIG_DAM.getId())
                .addStoryBoard("bigdam/common", BigDam::Common, CTPPPonderTags.CTPPPonder)
                .addStoryBoard("bigdam/common", BigDam::Work, CTPPPonderTags.CTPPPonder);

        helper.forComponents(CTPPMultiblockMachines.SMASHING_FACTORY.getId())
                .addStoryBoard("smashing_factory/common", SmashingFactory::Common, CTPPPonderTags.CTPPPonder);

        helper.forComponents(CTPPMultiblockMachines.KINETIC_GENERATOR.getId())
                .addStoryBoard("kinetic_generator/common", KineticGenerator::Common, CTPPPonderTags.CTPPPonder);

        helper.forComponents(CTPPMultiblockMachines.WINDMILL_CONTROL_CENTER.getId())
                .addStoryBoard("windmill_control_center/common", WindmillControlCenter::Common,
                        CTPPPonderTags.CTPPPonder);

        ResourceLocation[] kineticHatches = new ResourceLocation[GTValues.ALL_TIERS.length * 2];
        int index = 0;
        for (int tier : GTValues.ALL_TIERS) {
            kineticHatches[index++] = CTPPMachines.KINETIC_INPUT_BOX[tier].getId();
            kineticHatches[index++] = CTPPMachines.KINETIC_OUTPUT_BOX[tier].getId();
        }
        helper.forComponents(kineticHatches)
                .addStoryBoard("kinetic_hatch/common", KineticHatch::Common, CTPPPonderTags.KineticHatch);

        helper.forComponents(CTPPMachines.CARBON_BRUSHES.getId(), CTPPBlocks.GENERATOR_COIL.getId())
                .addStoryBoard("carbonbrushes/common", CarbonBrushes::ponder, CTPPPonderTags.CTPPPonder);

        CTPP.LOGGER.info("Ponder scenes initialized");
    }
}
