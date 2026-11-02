package com.mo_guang.ctpp.config;

import com.ctnhlang.CN;
import com.ctnhlang.EN;
import com.ctnhlang.Key;
import com.mo_guang.ctpp.CTPP;
import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;
import tech.vixhentx.mcmod.ctnhlib.langprovider.Lang;

@Config(id = CTPP.MODID)
public class MainConfig {

    @Key("config.screen.ctpp")
    @CN("CTPP设置")
    @EN("CTPP Configuration")
    public static Lang configScreen;

    @Key("config.ctpp.option.gtmConfig")
    @CN("GTM经典联动联动机器配置")
    @EN("Classic GTM Linkage Machinery Configuration")
    public static Lang configOptionGtmConfig;

    @Key("config.ctpp.option.enableGTMKineticOutputBox")
    @CN("是否启用GTM的应力输出仓")
    @EN("Enable GTM Kinetic Output Box")
    public static Lang configOptionEnableGtmKineticOutputBox;

    @Key("config.ctpp.option.kineticInputBoxTorqueMultiplier")
    @CN("应力输入仓的扭矩乘数")
    @EN("Kinetic Input Box Torque Multiplier")
    public static Lang configOptionKineticInputBoxTorqueMultiplier;

    @Key("config.ctpp.option.kineticOutputBoxTorqueMultiplier")
    @CN("应力输出仓的扭矩乘数")
    @EN("Kinetic Output Box Torque Multiplier")
    public static Lang configOptionKineticOutputBoxTorqueMultiplier;

    @Key("config.ctpp.option.enableGTMKineticCreateMixer")
    @CN("是否启用GTM的应力搅拌机")
    @EN("Enable GTM Kinetic Mixer")
    public static Lang configOptionEnableGtmKineticCreateMixer;

    @Key("config.ctpp.option.kineticCreateMixerSpeedMultiplier")
    @CN("比起同等级的电动搅拌机，应力搅拌机的处理速度倍率")
    @EN("Kinetic Mixer Speed Multiplier Compared to Mixer")
    public static Lang configOptionKineticCreateMixerSpeedMultiplier;

    @Key("config.ctpp.option.kineticCreateMixerRPMRequirement")
    @CN("应力搅拌转速要求")
    @EN("Kinetic Mixer RPM Requirement")
    public static Lang configOptionKineticCreateMixerRpmRequirement;

    @Key("config.ctpp.option.enableGTMElectricGearBox")
    @CN("是否启用GTM的电动齿轮箱")
    @EN("Enable Electric Gearbox")
    public static Lang configOptionEnableGtmElectricGearBox;

    @Key("config.ctpp.option.electricGearBoxRpmPerAmp")
    @CN("电力齿轮箱每安培输出转速")
    @EN("Electric Gearbox RPM Per Amp")
    public static Lang configOptionElectricGearBoxRpmPerAmp;

    @Key("config.ctpp.option.ctnhConfig")
    @CN("CTNH机器配置")
    @EN("CTNH Machinery Configuration")
    public static Lang configOptionCtnhConfig;

    @Key("config.ctpp.option.enableSmashingFactory")
    @CN("是否启用CTNH的粉碎工厂")
    @EN("Enable Smashing Factory")
    public static Lang configOptionEnableSmashingFactory;

    @Key("config.ctpp.option.smashingFactoryMaximumProcessingCapacity")
    @CN("粉碎工厂的最大处理能力（用整数来表示电压级）")
    @EN("Smashing Factory's Max Capacity (Integer for Voltage Tier)")
    public static Lang configOptionSmashingFactoryMaximumProcessingCapacity;

    @Key("config.ctpp.option.smashingFactorySpeedMultiplier")
    @CN("比起同等级的粉碎机，粉碎工厂的处理速度倍率")
    @EN("Smashing Factory Speed Multiplier Compared to Maceration")
    public static Lang configOptionSmashingFactorySpeedMultiplier;

    @Key("config.ctpp.option.smashingFactoryRPMRequirement")
    @CN("粉碎工厂的转速要求")
    @EN("Smashing Factory RPM Requirement")
    public static Lang configOptionSmashingFactoryRpmRequirement;

    @Key("config.ctpp.option.smashingFactoryStressRequirement")
    @CN("粉碎工厂的应力要求（等于原配方的电压需求乘以该倍数）")
    @EN("Smashing Factory Stress Load Factor (Recipe's Voltage × This Value)")
    public static Lang configOptionSmashingFactoryStressRequirement;

    @Key("config.ctpp.option.enableKineticGenerator")
    @CN("是否启用CTNH的应力发电机")
    @EN("Enable Kinetic Generator")
    public static Lang configOptionEnableKineticGenerator;

    @Key("config.ctpp.option.kineticGeneratorGeneratingBoost")
    @CN("应力发电机的电力产出加成")
    @EN("Kinetic Generator's EU Generation Boost")
    public static Lang configOptionKineticGeneratorGeneratingBoost;

    @Key("config.ctpp.option.kineticGeneratorGeneratingRequireLubricant")
    @CN("应力发电是否消耗润滑油")
    @EN("Kinetic Generation Require Lubricant")
    public static Lang configOptionKineticGeneratorGeneratingRequireLubricant;

    @Key("config.ctpp.option.kineticGeneratorGeneratingRequireLubricantAmount")
    @CN("应力发电的润滑油消耗量")
    @EN("Kinetic Generation Require Lubricant Amount")
    public static Lang configOptionKineticGeneratorGeneratingRequireLubricantAmount;

    @Key("config.ctpp.option.carbonBrushesMaxCoils")
    @CN("一个碳刷可接收的发电机线圈数量")
    @EN("Maximum Generator Coils Collected by One Carbon Brush")
    public static Lang configOptionCarbonBrushesMaxCoils;

    @Key("config.ctpp.option.carbonBrushesSuToEnergy")
    @CN("发电机线圈每单位应力的能量产出")
    @EN("Generator Coil Energy per Stress Unit")
    public static Lang configOptionCarbonBrushesSuToEnergy;

    @Key("config.ctpp.option.enableKineticSteamTurbine")
    @CN("是否启用CTNH的机械蒸汽涡轮")
    @EN("Enable Kinetic Steam Turbine")
    public static Lang configOptionEnableKineticSteamTurbine;

    @Key("config.ctpp.option.enableSteamTurbineGearBox")
    @CN("是否启用CTNH的蒸汽涡轮齿轮箱")
    @EN("Enable Steam Turbine GearBox")
    public static Lang configOptionEnableSteamTurbineGearBox;

    @Key("config.ctpp.option.steamPoweredKineticGeneratingBoost")
    @CN("蒸汽动力的应力产出加成")
    @EN("Steam Turbine Stress Output Boost")
    public static Lang configOptionSteamPoweredKineticGeneratingBoost;

    @Key("config.ctpp.option.enableSeaweedFarm")
    @CN("是否启用CTNH的海草农场")
    @EN("Enable Seaweed Farm")
    public static Lang configOptionEnableSeaweedFarm;

    @Key("config.ctpp.option.enableWindmillControlCenter")
    @CN("是否启用CTNH的风车控制中心")
    @EN("Enable Windmill Control Center")
    public static Lang configOptionEnableWindmillControlCenter;

    @Key("config.ctpp.option.enableBoomOfCreate")
    @CN("是否启用CTNH的聚爆应力厂")
    @EN("Enable Boom Of Create")
    public static Lang configOptionEnableBoomOfCreate;

    @Key("config.ctpp.option.clientConfig")
    @CN("客户端配置")
    @EN("Client Configuration")
    public static Lang configOptionClientConfig;

    @Key("config.ctpp.option.toolboxSounds")
    @CN("启用工具箱音效")
    @EN("Enable Toolbox Sounds")
    public static Lang configOptionToolboxSounds;

    @Key("config.ctpp.option.terminalMaxConnectionRange")
    @CN("接线柱最大连接范围")
    @EN("Maximum terminal connection range")
    public static Lang configOptionTerminalMaxConnectionRange;

    public static MainConfig INSTANCE;
    private static final Object LOCK = new Object();

    public static void init() {
        synchronized (LOCK) {
            if (INSTANCE == null) {
                INSTANCE = Configuration.registerConfig(MainConfig.class, ConfigFormats.yaml()).getConfigInstance();
            }
        }
    }

    @Configurable
    @Configurable.Comment("GTM's Origenal Configs")
    public GtmConfig gtmConfig = new GtmConfig();
    @Configurable
    @Configurable.Comment("CTNH's Configs")
    public CTNHConfig ctnhConfig = new CTNHConfig();
    @Configurable
    @Configurable.Comment("Client-only configurations")
    public ClientConfig clientConfig = new ClientConfig();

    public static class GtmConfig {

        @Configurable
        @Configurable.Comment("Is GTM KineticOutputBox Enabled?")
        public boolean enableGTMKineticOutputBox = true;
        @Configurable
        @Configurable.Comment("Torque multiplier of KineticInputBox relative to its voltage level (0.5~8.0)")
        @Configurable.DecimalRange(min = 0.5, max = 8.0)
        public float kineticInputBoxTorqueMultiplier = 4;
        @Configurable
        @Configurable.Comment("Torque multiplier of KineticOutputBox relative to its voltage level (0.5~8.0)")
        @Configurable.DecimalRange(min = 0.5, max = 8.0)
        public float kineticOutputBoxTorqueMultiplier = 4;
        @Configurable
        @Configurable.Comment("Is GTM KineticCreateMixer Enabled?")
        public boolean enableGTMKineticCreateMixer = true;
        @Configurable
        @Configurable.Comment("Processing speed Multiplier of KineticCreateMixer relative to its voltage level (0.5~4.0)")
        @Configurable.DecimalRange(min = 0.5, max = 4.0)
        public float kineticCreateMixerSpeedMultiplier = 2;
        @Configurable
        @Configurable.Comment("KineticCreateMixer's RPM Requirement (16~256)")
        @Configurable.Range(min = 16, max = 256)
        public int kineticCreateMixerRPMRequirement = 64;
        @Configurable
        @Configurable.Comment("Is GTM ElectricGearBox Enabled?")
        public boolean enableGTMElectricGearBox = true;
        @Configurable
        @Configurable.Comment("Electric Gearbox's RPM per amp (1~256)")
        @Configurable.Range(min = 1, max = 256)
        public int electricGearBoxRpmPerAmp = 16;
        @Configurable
        @Configurable.Comment("Stress per RPM applied to the Create network by the Kinetic Trading Machine (0~1024)")
        @Configurable.DecimalRange(min = 0.0, max = 1024.0)
        public float marketStressPerRpm = 64.0f;
    }

    public static class CTNHConfig {

        @Configurable
        @Configurable.Comment("Is CTNH SmashingFactory Enabled?")
        public boolean enableSmashingFactory = true;
        @Configurable
        @Configurable.Comment("SmashingFactory's Maximum Processing Capacity (0~9 for the voltage level)")
        public int smashingFactoryMaximumProcessingCapacity = 3;
        @Configurable
        @Configurable.Comment("SmashingFactory's Speed Multiplier relative to its voltage level (0.5~4.0)")
        @Configurable.DecimalRange(min = 0.5, max = 4.0)
        public float smashingFactorySpeedMultiplier = 2;
        @Configurable
        @Configurable.Comment("SmashingFactory's RPM Requirement (16~256)")
        @Configurable.Range(min = 16, max = 256)
        public int smashingFactoryRPMRequirement = 64;
        @Configurable
        @Configurable.Comment("SmashingFactory's Stress Requirement (1~1024)")
        @Configurable.DecimalRange(min = 1.0, max = 1024.0)
        public float smashingFactoryStressRequirement = 512;
        @Configurable
        @Configurable.Comment("Is CTNH KineticGenerator Enabled?")
        public boolean enableKineticGenerator = true;
        @Configurable
        @Configurable.Comment("KineticGenerator's Generating Boost (0.5~8.0)")
        @Configurable.DecimalRange(min = 0.5, max = 8.0)
        public float kineticGeneratorGeneratingBoost = 1;
        @Configurable
        @Configurable.Comment("Does KineticGenerator require lubricant?")
        public boolean kineticGeneratorGeneratingRequireLubricant = true;
        @Configurable
        @Configurable.Comment("KineticGenerator's Require Lubricant Amount (mL)")
        public int kineticGeneratorGeneratingRequireLubricantAmount = 1;
        @Configurable
        @Configurable.Comment("Maximum number of generator coils collected by one carbon brush")
        public int carbonBrushesMaxCoils = 8;
        @Configurable
        @Configurable.Comment("Energy generated per stress unit and tick by generator coils")
        @Configurable.DecimalRange(min = 0.0, max = Double.MAX_VALUE)
        public double carbonBrushesSuToEnergy = 0.029296875;
        @Configurable
        @Configurable.Comment("Is CTNH KineticSteamTurbine Enabled?")
        public boolean enableKineticSteamTurbine = true;
        @Configurable
        @Configurable.Comment("Steam Powered's Kinetic Generating Boost (0.5~8.0)")
        @Configurable.DecimalRange(min = 0.5, max = 8.0)
        public float steamPoweredKineticGeneratingBoost = 1;
        @Configurable
        @Configurable.Comment("Is CTNH SeaweedFarm Enabled?")
        public boolean enableSeaweedFarm = true;
        @Configurable
        @Configurable.Comment("Is CTNH WindmillControlCenter Enabled?")
        public boolean enableWindmillControlCenter = true;
        @Configurable
        @Configurable.Comment("Is CTNH BoomOfCreate Enabled?")
        public boolean enableBoomOfCreate = true;
    }

    public static class ClientConfig {

        @Configurable
        @Configurable.Comment("Whether toolbox opening and closing sounds are enabled")
        public boolean toolboxSounds = true;
    }

    @Configurable
    @Configurable.Comment("Voltage terminal configuration")
    public TerminalConfig terminalConfig = new TerminalConfig();

    public static class TerminalConfig {

        @Configurable
        @Configurable.Comment("Maximum distance between two voltage terminals (16..2147483647)")
        @Configurable.Range(min = 16, max = Integer.MAX_VALUE)
        public int terminalMaxConnectionRange = 32;
    }
}
