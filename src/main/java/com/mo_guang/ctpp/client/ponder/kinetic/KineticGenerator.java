package com.mo_guang.ctpp.client.ponder.kinetic;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

import com.mo_guang.ctpp.client.ponder.CTPPPonderSceneBuilder;
import com.mo_guang.ctpp.registry.CTPPMachines;
import tech.vixhentx.mcmod.ctnhlib.client.ponder.ui.MachineUI;

/**
 * 应力发电机（{@code ctpp:kinetic_generator}）的思索。
 * <p>
 * 坐标与 {@code assets/ctpp/ponder/kinetic_generator/common.nbt} 一致：主方块 (6,3,2)，
 * 线圈 x3..5/y2..4/z2..4，磁铁 36 格围成四圈，应力输入仓在西侧 x=1，
 * 机械升级仓与流体输入仓在 x=6，能量输出仓在东侧 x=7。
 */
public class KineticGenerator {

    private static final Vec3 MAGNET_PARK = new Vec3(-2d, 5d, 2d);

    /** 润滑油输入仓的界面；这一段只画，不改机器状态。 */
    private static final MachineUI LUBRICANT_HATCH_UI = MachineUI.of(GTMachines.FLUID_IMPORT_HATCH[GTValues.LV])
            .scale(0.6f);

    private KineticGenerator() {}

    public static void Common(SceneBuilder builder, SceneBuildingUtil util) {
        CTPPPonderSceneBuilder scene = new CTPPPonderSceneBuilder(builder);
        scene.title("kinetic_generator_common", "How to build the Kinetic Generator", "如何搭建应力发电机",
                "Kinetic Generator", "应力发电机");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.6f);
        scene.setSceneOffsetY(-1);
        scene.idle(10);
        scene.world().showSection(util.select().layer(0), Direction.DOWN);
        scene.idle(10);

        Selection controller = util.select().position(6, 3, 2);
        Selection magnets = util.select().fromTo(3, 1, 2, 5, 1, 4)
                .add(util.select().fromTo(3, 5, 2, 5, 5, 4))
                .add(util.select().fromTo(3, 2, 1, 5, 4, 1))
                .add(util.select().fromTo(3, 2, 5, 5, 4, 5));
        Selection body = util.select().fromTo(1, 1, 1, 7, 5, 5).substract(magnets).substract(controller);
        Selection coilCore = util.select().fromTo(3, 2, 2, 5, 4, 4);

        scene.world().showSection(controller, Direction.DOWN);
        scene.showText(60, "First, place the Kinetic Generator controller.", "首先放置应力发电机主方块。")
                .pointAt(util.vector().blockSurface(util.grid().at(6, 3, 2), Direction.WEST))
                .attachKeyFrame();
        scene.idle(60);

        scene.overlay().showControls(util.vector().blockSurface(util.grid().at(6, 3, 2), Direction.WEST),
                Pointing.LEFT, 40)
                .rightClick()
                .withItem(GTItems.TERMINAL.asStack())
                .whileSneaking();
        scene.showText(60, "Use a terminal while sneaking to place the whole structure in one click.",
                "拿着终端潜行右键，一键放置整个结构。")
                .attachKeyFrame();
        scene.idle(30);
        scene.world().showSection(body, Direction.DOWN);
        scene.idle(40);

        ElementLink<WorldSectionElement> magnetLink = scene.world()
                .showIndependentSection(magnets, Direction.DOWN);
        scene.idle(20);

        scene.world().moveSection(magnetLink, MAGNET_PARK, 20);
        scene.overlay().showOutline(PonderPalette.RED, "coils", coilCore, 100);
        scene.showText(90,
                "Setting the magnets aside for now. The coils are the heart of the machine: each tier adds 10 percentage points of efficiency, starting at 90.",
                "先把磁铁挪到一边。线圈是这台机器的核心：每提升一级效率增加 10 个百分点，初始为 90 个百分点。")
                .pointAt(util.vector().blockSurface(util.grid().at(4, 3, 3), Direction.UP))
                .attachKeyFrame();
        scene.idle(120);

        scene.world().moveSection(magnetLink, MAGNET_PARK.scale(-1d), 20);
        scene.overlay().showOutline(PonderPalette.BLUE, "magnets", magnets, 90);
        scene.showText(80,
                "The magnets around the coils build up the magnetic field. The stronger the field, the larger the share of stress that turns into EU.",
                "线圈四周的磁铁负责建立磁场。磁场越强，应力转化为 EU 的比例越高。")
                .pointAt(util.vector().blockSurface(util.grid().at(4, 5, 3), Direction.UP))
                .attachKeyFrame();
        scene.idle(100);

        scene.world().rotateSection(magnetLink, 360, 0, 0, 200);
        scene.showText(80,
                "Once the structure forms, the magnets spin around the coils and turn the incoming stress into electricity.",
                "结构成型后，磁铁会绕着线圈旋转，把输入的应力转化为电力。")
                .attachKeyFrame();
        scene.idle(210);

        scene.world().setBlock(util.grid().at(1, 3, 3),
                CTPPMachines.KINETIC_INPUT_BOX[GTValues.EV].defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.WEST),
                true);
        scene.overlay().showOutline(PonderPalette.BLUE, "kin_in", util.select().position(1, 3, 3), 80);
        scene.showText(80,
                "Kinetic input hatches go on this side and feed the stress in. The machine needs at least 512 su to start.",
                "应力输入仓装在这一侧，负责输入应力。机器至少需要 512 su 才能启动。")
                .pointAt(util.vector().blockSurface(util.grid().at(1, 3, 3), Direction.WEST))
                .attachKeyFrame();
        scene.idle(90);

        scene.showText(100,
                "All inputs must run at the same speed, and a kinetic hatch of HV or above costs 10 percentage points of efficiency for every tier above MV.",
                "所有输入必须以相同转速运行。另外，HV 及以上的动力仓每高于 MV 一级，就扣掉 10 个百分点效率。")
                .pointAt(util.vector().blockSurface(util.grid().at(1, 3, 3), Direction.WEST))
                .attachKeyFrame();
        scene.idle(110);

        scene.rotateCameraY(180);
        scene.idle(40);

        scene.world().setBlock(util.grid().at(6, 2, 4),
                CTPPMachines.MECHANICAL_UPGRADE_BUS[GTValues.LV].defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.SOUTH),
                true);
        scene.overlay().showOutline(PonderPalette.RED, "upgrade", util.select().position(6, 2, 4), 80);
        scene.showText(80,
                "The Mechanical Upgrade Bus sets the mechanical tier, which caps how much EU the generator may output per tick.",
                "机械升级仓决定机械等级，机械等级限制了机器每 tick 的发电上限。")
                .pointAt(util.vector().blockSurface(util.grid().at(6, 2, 4), Direction.SOUTH))
                .attachKeyFrame();
        scene.idle(90);

        scene.world().setBlock(util.grid().at(6, 4, 4),
                GTMachines.FLUID_IMPORT_HATCH[GTValues.LV].defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.SOUTH),
                true);
        scene.showUI(LUBRICANT_HATCH_UI).at(util.vector().topOf(util.grid().at(6, 4, 4))).machinePos(util.grid().at(6, 4, 4))
                .tank(0)
                .withFluid(new FluidStack(GTMaterials.Lubricant.getFluid(), 1000), 20)
                .show(190);
        scene.showText(100,
                "Lubricant lives in the fluid import hatch itself: the hatch's own UI is the tank you fill, and the generator keeps running.",
                "润滑油就装在这个流体输入仓里：仓室自己的界面就是那个要灌满的储罐，发电机就能持续运转。")
                .pointAt(util.vector().blockSurface(util.grid().at(6, 4, 4), Direction.SOUTH))
                .attachKeyFrame();
        scene.idle(200);

        scene.world().setBlock(util.grid().at(7, 3, 3),
                GTMachines.ENERGY_OUTPUT_HATCH[GTValues.LV].defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.EAST),
                true);
        scene.overlay().showOutline(PonderPalette.GREEN, "eu_out", util.select().position(7, 3, 3), 80);
        scene.showText(90,
                "The EU that the generator makes leaves through the energy output hatch. The base ratio is 128 su to 1 EU.",
                "发出来的 EU 从能量输出仓导出。基础比例为 128 su 换 1 EU。")
                .pointAt(util.vector().blockSurface(util.grid().at(7, 3, 3), Direction.EAST))
                .attachKeyFrame();
        scene.effects().emitParticles(
                util.vector().centerOf(7, 3, 3),
                scene.effects().simpleParticleEmitter(ParticleTypes.ELECTRIC_SPARK, new Vec3(0.05, 0, 0.05)),
                2f, 60);
        scene.idle(100);

        scene.rotateCameraY(-180);
        scene.idle(40);
        scene.markAsFinished();
    }
}
