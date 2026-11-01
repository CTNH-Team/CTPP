package com.mo_guang.ctpp.registry;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;

import com.ctnhlang.CN;
import com.ctnhlang.EN;
import com.ctnhlang.Prefix;
import com.mo_guang.ctpp.CTPP;
import com.mo_guang.ctpp.CTPPRegistration;
import com.mo_guang.ctpp.api.CTPPPartAbility;
import com.mo_guang.ctpp.api.KineticMachineDefinition;
import com.mo_guang.ctpp.common.block.KineticMachineBlock;
import com.mo_guang.ctpp.common.blockentity.KineticMachineBlockEntity;
import com.mo_guang.ctpp.common.machine.market.KineticTradingMachine;
import com.mo_guang.ctpp.common.machine.market.TradingMachine;
import com.mo_guang.ctpp.common.machine.multiblock.part.KineticPartMachine;
import com.mo_guang.ctpp.common.machine.multiblock.part.MechanicalUpgradePartMachine;
import com.mo_guang.ctpp.common.machine.simple.CarbonBrushesGeneratorMachine;
import com.mo_guang.ctpp.common.machine.simple.ElectricGearBoxMachine;
import com.mo_guang.ctpp.config.MainConfig;
import com.mo_guang.ctpp.util.ItemAxisBuilder;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;
import tech.vixhentx.mcmod.ctnhlib.registrate.builders.CTNHMachineBuilder;

import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.GTValues.ALL_TIERS;
import static com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.*;
import static com.mo_guang.ctpp.CTPPRegistration.REGISTRATE;
import static com.mo_guang.ctpp.common.data.model.CTPPMachineModels.createTieredCustomModel;
import static com.mo_guang.ctpp.config.ConfigUtils.*;
import static com.mo_guang.ctpp.registry.CTPPCreativeModeTabs.MACHINE;

@Prefix("machine")
public class CTPPMachines {

    static {
        REGISTRATE.creativeModeTab(() -> MACHINE);
    }
    public static final MachineDefinition[] MECHANICAL_UPGRADE_BUS = new MachineDefinition[GTValues.TIER_COUNT];

    public static KineticMachineDefinition[] ELECTRIC_GEAR_BOX_2A;
    public static KineticMachineDefinition[] ELECTRIC_GEAR_BOX_8A;
    public static KineticMachineDefinition[] ELECTRIC_GEAR_BOX_16A;
    public static KineticMachineDefinition[] ELECTRIC_GEAR_BOX_32A;
    // public static final KineticMachineDefinition[] KINETIC_MIXER =
    // CTPPRegistration.conditionalRegistration(gtmEnabled("GTMKineticCreateMixer"),() ->
    // registerSimpleKineticElectricMachine("kinetic_mixer",CTPPRecipeTypes.KINETIC_MIXER_RECIPES, LOW_TIERS));
    public static KineticMachineDefinition[] KINETIC_INPUT_BOX;
    public static KineticMachineDefinition[] KINETIC_OUTPUT_BOX;
    public static KineticMachineDefinition CARBON_BRUSHES;
    public static MachineDefinition[] PLACEABLE_EMITTER;

    public static KineticMachineDefinition[] registerElectricGearBox(int maxAmps, int... tiers) {
        return CTPPRegistration.conditionalRegistration(gtmEnabled("GTMElectricGearBox"),
                () -> registerKineticTieredMachines("electric_gear_box_%sa".formatted(maxAmps),
                        "%sA电力齿轮箱".formatted(maxAmps),
                        (tier, id) -> new KineticMachineDefinition(id, true, GTValues.V[tier]).setFrontRotation(true),
                        (holder, tier) -> new ElectricGearBoxMachine(holder, tier, maxAmps), (tier, builder) -> builder
                                .langValue(VNF[tier] + " %sA Electric Gear Box".formatted(maxAmps))
                                .rotationState(RotationState.ALL)
                                .model(createTieredCustomModel(
                                        CTPP.id("block/machine/electric_gear_box")))
                                .tier(tier)
                                .register(),
                        tiers));
    }

    // public static KineticMachineDefinition[] registerSimpleKineticElectricMachine(String name, GTRecipeType
    // recipeType,
    // int... tiers) {
    // return registerKineticTieredMachines(name, (tier, id) -> new KineticMachineDefinition(id, false,
    // GTValues.V[tier]),
    // (holder, tier) -> new SimpleKineticElectricWorkableMachine(holder, tier, defaultTankSizeFunction),
    // (tier, builder) -> builder
    // .langValue("%s %s %s".formatted(VLVH[tier], toEnglishName(name), VLVT[tier]))
    // .rotationState(RotationState.NON_Y_AXIS)
    // .editableUI(SimpleTieredMachine.EDITABLE_UI_CREATOR.apply(GTCEu.id(name), recipeType))
    // .recipeType(recipeType)
    // .recipeModifier(
    // GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
    // .model(createWorkableTieredCustomMachineModel(CTPP.id("block/machine/kinetic_electric_machine"),GTCEu.id("block/machines/"+name))
    // )
    // .tier(tier)
    // .tooltips(explosion())
    // .tooltips(workableTiered(tier, GTValues.V[tier], GTValues.V[tier] * 64, recipeType,
    // defaultTankSizeFunction.apply(tier), true))
    // .register(),
    // tiers);
    // }

    public static KineticMachineDefinition[] registerKineticTieredMachines(String name,
                                                                           String cnname,
                                                                           BiFunction<Integer, ResourceLocation, KineticMachineDefinition> definitionFactory,
                                                                           BiFunction<IMachineBlockEntity, Integer, MetaMachine> factory,
                                                                           BiFunction<Integer, CTNHMachineBuilder<KineticMachineDefinition>, KineticMachineDefinition> builder,
                                                                           int... tiers) {
        KineticMachineDefinition[] definitions = new KineticMachineDefinition[GTValues.TIER_COUNT];
        for (int tier : tiers) {
            var register = REGISTRATE.machine(VN[tier].toLowerCase(Locale.ROOT) + "_" + name,
                    VNF[tier] + cnname,
                    id -> definitionFactory.apply(tier, id),
                    holder -> factory.apply(holder, tier),
                    KineticMachineBlock::new,
                    MetaMachineItem::new,
                    KineticMachineBlockEntity::create)
                    .tier(tier)
                    .blockProp(BlockBehaviour.Properties::noOcclusion)
                    .hasBER(false)
                    .onBlockEntityRegister(KineticMachineBlockEntity::onBlockEntityRegister)
                    .itemBuilder(ItemAxisBuilder::addShaft);
            definitions[tier] = builder.apply(tier, register);
        }
        return definitions;
    }

    @CN("输出发电机线圈产生的能量")
    @EN("Energy Output for Generator Coil")
    static Lang carbon_brushes;

    @CN("交易机：按单据买卖物品与流体")
    @EN("Trading Machine: buys and sells items or fluids from tickets")
    static Lang market_trading;

    @CN("放入单据、货物与金钱后按单据逐条自动交易")
    @EN("Load tickets, goods and money; tickets are settled one line at a time")
    static Lang market_trading_tooltip;

    @CN("每笔交易移动 1 个物品 / 1mB 流体，价格即单价")
    @EN("Each trade moves 1 item or 1mB of fluid; the ticket price is the unit price")
    static Lang market_batch;

    @CN("货物模式（无 / 自动输出 / 代理）在方向设置中配置")
    @EN("Goods modes (None / Auto Output / Proxy) are set in Directional Setup")
    static Lang market_io_hint;

    /**
     * Registers the three trading machines: stress-driven, LV and MV.
     * <p>
     * The stress variant uses the kinetic block family so Create can drive it; the two electric variants use the
     * plain tiered hull. All three share one class, so only the block/energy plumbing differs.
     */
    private static void registerMarketMachines() {
        // The stress variant is a single machine, so it is registered directly instead of through the tiered helper
        // (which would prefix it with the voltage tier and produce "lv_kinetic_market").
        REGISTRATE.machine("kinetic_market",
                "应力交易机",
                id -> new KineticMachineDefinition(id, false, MainConfig.INSTANCE.gtmConfig.marketStressPerTrade)
                        .setFrontRotation(true),
                holder -> new KineticTradingMachine(holder),
                KineticMachineBlock::new,
                MetaMachineItem::new,
                KineticMachineBlockEntity::create)
                .cnLangValue("应力交易机")
                .langValue("Kinetic Trading Machine")
                .tier(LV)
                .blockProp(BlockBehaviour.Properties::noOcclusion)
                .hasBER(false)
                .onBlockEntityRegister(KineticMachineBlockEntity::onBlockEntityRegister)
                .itemBuilder(ItemAxisBuilder::addShaft)
                .rotationState(RotationState.ALL)
                // Jack's Economy's mechanical exporter model verbatim, retargeted at CTPP's copy of its textures:
                // a `$` shopfront, a gearbox at the back, and machine casing on the sides.
                //
                // The base lives under models/block/market/ rather than models/block/machine/ on purpose: GT's
                // model loader writes its own wrapper to models/block/machine/<id>, so keeping the hand-written
                // base out of that path avoids the two trees colliding during processResources.
                .model((ctx, prov, modelBuilder) -> {
                    var model = prov.models().getExistingFile(CTPP.id("block/market/kinetic_market"));
                    modelBuilder.forAllStatesModels(state -> model);
                })
                .tooltips(market_trading.translate(), market_trading_tooltip.translate(),
                        market_batch.translate(), market_io_hint.translate())
                .register();

        for (int tier : new int[] { LV, MV }) {
            final int machineTier = tier;
            REGISTRATE.machine(VN[tier].toLowerCase(Locale.ROOT) + "_market",
                    holder -> new TradingMachine(holder, machineTier))
                    .langValue(VNF[tier] + " Trading Machine")
                    .cnLangValue(VNF[tier] + " 交易机")
                    .rotationState(RotationState.ALL)
                    .tier(tier)
                    .model(createTieredCustomModel(CTPP.id("block/machine/market")))
                    .tooltips(market_trading.translate(), market_trading_tooltip.translate(),
                            market_batch.translate(), market_io_hint.translate())
                    .register();
        }
    }

    @CN("§e最大输出电流§r %sA")
    @EN("§eMax Output Amperage:§r %sA")
    static Lang max_output_amperage;

    @CN("应力影响：%s su")
    @EN("Kinetic Effect：%s su")
    static Lang kineticInputBoxTooltip;

    @CN("应力影响：%s su")
    @EN("Kinetic Effect：%s su")
    static Lang kineticOutputBoxTooltip;

    public static void init() {
        PLACEABLE_EMITTER = new MachineDefinition[GTValues.TIER_COUNT];
        // machine items are unobtainable intermediates: placement goes through the vanilla GT
        // emitter items, so keep the machine items out of the creative tab (EMI: CTPPEmiPlugin)
        REGISTRATE.creativeModeTab(() -> null);
        for (int tier : new int[] { LV, MV, HV, EV, IV, LuV, ZPM, UV, UHV, UEV, UIV, UXV, OpV }) {
            PLACEABLE_EMITTER[tier] = REGISTRATE
                    .machine(GTValues.VN[tier].toLowerCase(Locale.ROOT) + "_placeable_emitter",
                            holder -> new com.mo_guang.ctpp.common.machine.simple.PlaceableEmitterMachine(holder,
                                    tier))
                    .tier(tier)
                    .langValue(VNF[tier] + " Placeable Emitter")
                    .cnLangValue(VNF[tier] + " 可放置发射器")
                    .rotationState(RotationState.ALL)
                    // the OBJ model doesn't fill the block: a full-cube occluding block would cull
                    // the neighbors' touching faces (making the block below look transparent)
                    .shape(Block.box(3, 3, 2, 13, 14, 14))
                    .blockProp(BlockBehaviour.Properties::noOcclusion)
                    .tooltips(
                            Component.translatable("gtceu.universal.tooltip.voltage_in",
                                    FormattingUtil.formatNumbers(V[tier]), VNF[tier]),
                            Component.translatable("gtceu.universal.tooltip.amperage_in", 1))
                    .model((ctx, prov, modelBuilder) -> {
                        var emitterModel = prov.models().getExistingFile(CTPP.id("block/emitter/emitter"));
                        modelBuilder.forAllStatesModels(state -> emitterModel);
                    })
                    .register();
        }
        REGISTRATE.creativeModeTab(() -> MACHINE);
        KINETIC_INPUT_BOX = registerKineticTieredMachines(
                "kinetic_input_box",
                "应力输入箱",
                (tier,
                 id) -> new KineticMachineDefinition(id, false,
                         GTValues.V[tier] * MainConfig.INSTANCE.gtmConfig.kineticInputBoxTorqueMultiplier)
                         .setFrontRotation(true),
                (holder, tier) -> new KineticPartMachine(holder, tier, IO.IN), (tier, builder) -> builder
                        .langValue(VNF[tier] + " Kinetic Input Box")
                        .tooltips(
                                kineticInputBoxTooltip.translate(
                                        FormattingUtil.formatNumbers(GTValues.V[tier] *
                                                MainConfig.INSTANCE.gtmConfig.kineticInputBoxTorqueMultiplier)),
                                Component.translatable("gtceu.part_sharing.disabled"))
                        .rotationState(RotationState.ALL)
                        .abilities(CTPPPartAbility.INPUT_KINETIC)
                        .modelProperty(GTMachineModelProperties.IS_FORMED, false)
                        .model(createTieredCustomModel(CTPP.id("block/machine/part/kinetic_input_box"))
                                .andThen((ctx, prov, model) -> model.addReplaceableTextures("bottom", "top", "side")))
                        .tier(tier)
                        .register(),
                ALL_TIERS);

        KINETIC_OUTPUT_BOX = registerKineticTieredMachines("kinetic_output_box",
                "应力输出箱",
                (tier,
                 id) -> new KineticMachineDefinition(id, true,
                         GTValues.V[tier] * MainConfig.INSTANCE.gtmConfig.kineticOutputBoxTorqueMultiplier)
                         .setFrontRotation(true),
                (holder, tier) -> new KineticPartMachine(holder, tier, IO.OUT), (tier, builder) -> builder
                        .langValue(VNF[tier] + " Kinetic Output Box")
                        .tooltips(
                                kineticOutputBoxTooltip.translate(
                                        FormattingUtil.formatNumbers(GTValues.V[tier] *
                                                MainConfig.INSTANCE.gtmConfig.kineticInputBoxTorqueMultiplier)),
                                Component.translatable("gtceu.part_sharing.disabled"))
                        .rotationState(RotationState.ALL)
                        .abilities(CTPPPartAbility.OUTPUT_KINETIC)
                        .modelProperty(GTMachineModelProperties.IS_FORMED, false)
                        .model(createTieredCustomModel(CTPP.id("block/machine/part/kinetic_output_box"))
                                .andThen((ctx, prov, model) -> model.addReplaceableTextures("bottom", "top", "side")))
                        .tier(tier)
                        .register(),
                ALL_TIERS);

        registerMarketMachines();

        CARBON_BRUSHES = REGISTRATE.machine("carbon_brushes",
                "碳刷",
                id -> new KineticMachineDefinition(id, false,
                        0)
                        .setFrontRotation(true),
                holder -> new CarbonBrushesGeneratorMachine(holder, LV, genericGeneratorTankSizeFunction),
                KineticMachineBlock::new,
                MetaMachineItem::new,
                KineticMachineBlockEntity::create)
                .model((ctx, prov, builder) -> {
                    ModelFile parentModel = prov.models().getExistingFile(
                            CTPP.id("block/machine/carbon_brushes/base"));
                    BlockModelBuilder model = prov.models().nested().parent(parentModel);
                    // Apply a 90° Y rotation so the model matches CreateNewAge's orientation
                    builder.forAllStates(state -> ConfiguredModel.builder()
                            .modelFile(model)
                            .rotationX(90)
                            .build());
                })
                .rotationState(RotationState.ALL)
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .tier(LV)
                .itemBuilder(ItemAxisBuilder::addShaft)
                .tooltips(List.of(
                        Component.translatable("gtceu.universal.tooltip.voltage_out",
                                FormattingUtil.formatNumbers(V[LV]), VNF[LV]),
                        max_output_amperage.translate("16"),
                        Component.translatable("gtceu.universal.tooltip.energy_storage_capacity",
                                FormattingUtil.formatNumbers(2048)),
                        carbon_brushes.translate()))
                .hasBER(false)
                .blockProp(BlockBehaviour.Properties::noOcclusion)
                .onBlockEntityRegister(KineticMachineBlockEntity::onCarbonBrushesBlockEntityRegister)
                .register();

        ELECTRIC_GEAR_BOX_2A = registerElectricGearBox(2, LOW_TIERS);
        ELECTRIC_GEAR_BOX_8A = registerElectricGearBox(8, LOW_TIERS);
        ELECTRIC_GEAR_BOX_16A = registerElectricGearBox(16, LOW_TIERS);
        ELECTRIC_GEAR_BOX_32A = registerElectricGearBox(32, LOW_TIERS);

        for (int tier = LV; tier <= HV; tier++) {
            final int machineTier = tier;
            MECHANICAL_UPGRADE_BUS[tier] = REGISTRATE
                    .machine(VN[tier].toLowerCase(Locale.ROOT) + "_mechanical_upgrade_bus",
                            holder -> new MechanicalUpgradePartMachine(holder, machineTier))
                    .cnLangValue(VNF[tier] + "机械升级仓")
                    .langValue(VNF[tier] + " Mechanical Upgrade Bus")
                    .tier(tier)
                    .rotationState(RotationState.ALL)
                    .abilities(CTPPPartAbility.MECHANICAL_UPGRADE)
                    .modelProperty(GTMachineModelProperties.IS_FORMED, false)
                    .overlayTieredHullModel(GTCEu.id("block/machine/part/item_passthrough_hatch"))
                    .register();
        }
    }
}
