package com.mo_guang.ctpp.registry;

import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.item.component.FoodStats;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import com.mo_guang.ctpp.common.item.debug.ContraptionDebugToolItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.mo_guang.ctpp.CTPPRegistration.REGISTRATE;

public class CTPPItems {

    public static void init() {
        // Coins and tickets register through their own holder; touching it here keeps the ordering explicit.
        CTPPMarketItems.init();
    }

    public static ItemEntry<Item> BASIC_MECHANISM = REGISTRATE.item("basic_mechanism", Item::new).cnlang("基础构件")
            .register();
    public static ItemEntry<Item> INCOMPLETE_BASIC_MECHANISM = REGISTRATE.item("incomplete_basic_mechanism", Item::new)
            .cnlang("未完成的基础构件").register();
    public static ItemEntry<Item> INCOMPLETE_STEEL_MECHANISM = REGISTRATE
            .item("incomplete_steel_mechanism", Item::new)
            .cnlang("未完成的钢铁构件")
            .lang("Incomplete Steel Mechanism")
            .register();
    public static ItemEntry<Item> STEEL_MECHANISM = REGISTRATE.item("steel_mechanism", Item::new).cnlang("钢铁构件")
            .register();
    public static ItemEntry<ComponentItem> DOUBLE_BLAZE_CAKE = REGISTRATE
            .item("double_blaze_cake", ComponentItem::create)
            .cnlang("双层烈焰蛋糕")
            .lang("Double Blaze Cake")
            .onRegister(item -> {
                var builder = new FoodProperties.Builder().alwaysEat();
                var effect = ForgeRegistries.MOB_EFFECTS.getValue(
                        ResourceLocation.tryBuild("legendarysurvivaloverhaul", "cold_immunity"));
                if (effect != null) {
                    builder.effect(() -> new MobEffectInstance(effect, 36000, 10), 1.0f);
                }
                item.attachComponents(new FoodStats(builder.build()));
                item.burnTime(30000);
            })
            .register();
    public static ItemEntry<ContraptionDebugToolItem> CONTRAPTION_LOCATOR = REGISTRATE
            .item("contraption_locator", p -> new ContraptionDebugToolItem(p, ContraptionDebugToolItem.Mode.LOCATE))
            .cnlang("装置定位器")
            .lang("Contraption Locator")
            .model((ctx, prov) -> prov.generated(ctx, prov.mcLoc("item/stick")))
            .register();
    public static ItemEntry<ContraptionDebugToolItem> CONTRAPTION_ASSEMBLER = REGISTRATE
            .item("contraption_assembler", p -> new ContraptionDebugToolItem(p, ContraptionDebugToolItem.Mode.ASSEMBLE))
            .cnlang("装置组装器")
            .lang("Contraption Assembler")
            .model((ctx, prov) -> prov.generated(ctx, prov.mcLoc("item/stick")))
            .register();
    public static ItemEntry<ContraptionDebugToolItem> CONTRAPTION_DISASSEMBLER = REGISTRATE
            .item("contraption_disassembler",
                    p -> new ContraptionDebugToolItem(p, ContraptionDebugToolItem.Mode.DISASSEMBLE))
            .cnlang("装置解组装器")
            .lang("Contraption Disassembler")
            .model((ctx, prov) -> prov.generated(ctx, prov.mcLoc("item/stick")))
            .register();
    // public static ItemEntry<RotationWandItem> ROTATION_WAND = REGISTRATE.item("rotation_wand", p -> new
    // RotationWandItem(p)).register();
    // public static final EntityEntry<SimpleRotatingContraptionEntity> ROTATING_CONTRAPTION =
    // REGISTRATE.movingEntity("rotating_contraption",
    // (EntityType.EntityFactory<SimpleRotatingContraptionEntity>) SimpleRotatingContraptionEntity::new,
    // MobCategory.MISC)
    // .properties(builder -> builder
    // .sized(1.0f, 1.0f) // 实体大小
    // .setTrackingRange(256)
    // .setUpdateInterval(1)
    // .fireImmune() // 可选
    // )
    // .renderer(() -> ContraptionEntityRenderer<SimpleRotatingContraptionEntity>::new)
    // .register();
}
