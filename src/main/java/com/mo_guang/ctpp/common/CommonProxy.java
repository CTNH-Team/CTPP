package com.mo_guang.ctpp.common;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CombinedDirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.common.item.tool.rotation.CustomBlockRotations;
import com.gregtechceu.gtceu.common.unification.material.MaterialRegistryManager;

import net.minecraft.core.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.mo_guang.ctpp.CTPP;
import com.mo_guang.ctpp.CTPPRegistration;
import com.mo_guang.ctpp.api.CTPPRecipeConditions;
import com.mo_guang.ctpp.client.ponder.CTPPPonderPlugin;
import com.mo_guang.ctpp.common.block.VoltageTerminalBlock;
import com.mo_guang.ctpp.common.data.GTArmInteractionPointTypes;
import com.mo_guang.ctpp.common.machine.market.MarketIoConfigHandler;
import com.mo_guang.ctpp.common.machine.market.TradingMachine;
import com.mo_guang.ctpp.config.MainConfig;
import com.mo_guang.ctpp.data.CTPPDatagen;
import com.mo_guang.ctpp.data.recipe.builder.CTPPRecipeProvider;
import com.mo_guang.ctpp.data.recipe.fanprocessing.CTPPFanProcessingTypes;
import com.mo_guang.ctpp.data.recipe.fanprocessing.CTPPRecipeTypeInfo;
import com.mo_guang.ctpp.registry.*;
import com.mo_guang.ctpp.registry.CTPPNetwork;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.tterrag.registrate.util.entry.BlockEntry;
import tech.vixhentx.mcmod.ctnhlib.client.ponder.CTNHPonderLang;

import static com.mo_guang.ctpp.registry.CTPPBlocks.VOLTAGE_TERMINALS;

@SuppressWarnings("removal")
public class CommonProxy {

    public CommonProxy() {
        init();
        CTPPNetwork.init();
        MainConfig.init();
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.register(this);
    }

    public void init() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        CTPPCreativeModeTabs.init();
        CTPPMenus.init();
        CTPPRegistration.REGISTRATE.registerRegistrate();
        CTPPDatagen.init();
        CTPPRecipeTypeInfo.register(modEventBus);
        CTPPFanProcessingTypes.register(modEventBus);
        modEventBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        modEventBus.addGenericListener(RecipeConditionType.class, this::registerRecipeConditions);
        modEventBus.addGenericListener(GTRecipeType.class, this::registerRecipeTypes);
    }

    @SubscribeEvent
    public void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Registry.register(
                    CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE,
                    new ResourceLocation("ctpp", "gt_machine"),
                    new GTArmInteractionPointTypes.GTMachineType());
            // Give trading machines a "goods" row in GT's directional setup, alongside GT's own io handlers.
            CombinedDirectionalFancyConfigurator
                    .registerConfigHandler(machine -> machine instanceof TradingMachine trading ?
                            () -> new MarketIoConfigHandler(trading) : null);
            for (BlockEntry<VoltageTerminalBlock> terminal : VOLTAGE_TERMINALS) {
                if (terminal != null) {
                    CustomBlockRotations.registerCustomRotation(terminal.get(), VoltageTerminalBlock.ROTATION_BEHAVIOR);
                }
            }
        });
    }

    @SubscribeEvent
    public void addMaterialFlag(MaterialEvent event) {
        GTMaterialAddon.init();
    }

    public void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        CTPPMachines.init();
        CTPPItems.init();
        CTPPMultiblockMachines.init();
    }

    public void registerRecipeTypes(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        CTPPRecipeTypes.init();
    }

    public void registerRecipeConditions(GTCEuAPI.RegisterEvent<ResourceLocation, RecipeConditionType> event) {
        CTPPRecipeConditions.init();
    }

    @SubscribeEvent
    public void gatherData(GatherDataEvent event) {
        CTPPDatagen.addToolboxData(event);
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        if (event.includeServer()) {
            CTPPRecipeProvider.registerAllProcessing(generator, output);
        }
        if (event.includeClient()) {
            CTNHPonderLang.init(new CTPPPonderPlugin());
        }
    }

    @SubscribeEvent
    public void registerMaterial(MaterialRegistryEvent event) {
        MaterialRegistryManager.getInstance().createRegistry(CTPP.MODID);
    }

    @SubscribeEvent
    public void registerMaterial(MaterialEvent event) {
        CreateMaterials.init();
    }
}
