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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;

import com.mo_guang.ctpp.client.ponder.CTPPPonderSceneBuilder;
import com.mo_guang.ctpp.registry.CTPPMachines;
import tech.vixhentx.mcmod.ctnhlib.client.ponder.ui.MachineUI;

/**
 * 风车控制中心。坐标与 storyboard（assets/ctpp/ponder/windmill_control_center/common.nbt，size [21,27,19]）逐格一致：
 * 主方块 (10,1,7)，外壳 x5..15 y1..5 z7..17，屋顶风车轴承 (10,5,12)，
 * 转子（线性底盘 + 羊毛）x5..15 y6..26 z7..17，绕 (10,5,12) 的竖直轴旋转；
 * 两台示范风车：轴承 (3,1,3) 与 (17,1,3)，5x5 十字叶片在 y2..3。
 */
public class WindmillControlCenter {

    /** 润滑油输入仓的界面；这一段只画，不改机器状态。 */
    private static final MachineUI LUBRICANT_HATCH_UI = MachineUI.of(GTMachines.FLUID_IMPORT_HATCH[GTValues.LV])
            .scale(0.6f);

    private static final BlockPos CONTROLLER = new BlockPos(10, 1, 7);
    private static final BlockPos BEARING = new BlockPos(10, 5, 12);
    private static final BlockPos LUBRICANT_HATCH = new BlockPos(12, 1, 7);
    private static final BlockPos UPGRADE_BUS = new BlockPos(8, 2, 7);
    private static final BlockPos OUTPUT_BOX_FRONT = new BlockPos(5, 1, 12);
    private static final BlockPos[] OUTPUT_BOXES = {
            new BlockPos(5, 1, 10), OUTPUT_BOX_FRONT, new BlockPos(5, 1, 14)
    };
    private static final BlockPos DEMO_A = new BlockPos(3, 1, 3);
    private static final BlockPos DEMO_B = new BlockPos(17, 1, 3);

    private WindmillControlCenter() {}

    public static void Common(SceneBuilder builder, SceneBuildingUtil util) {
        CTPPPonderSceneBuilder scene = new CTPPPonderSceneBuilder(builder);
        scene.title("windmill_control_center_common", "How to build Windmill Control Center", "如何搭建风车控制中心");
        scene.configureBasePlate(0, -1, 21);
        scene.scaleSceneView(0.18f);
        scene.setSceneOffsetY(-12.5f);

        Selection controller = util.select().position(CONTROLLER);
        Selection shell = util.select().fromTo(5, 1, 7, 15, 5, 17);
        Selection rotor = util.select().fromTo(5, 6, 7, 15, 26, 17);
        Selection outputWall = util.select().fromTo(5, 1, 10, 5, 1, 14);
        Selection demoSails = util.select().fromTo(1, 2, 1, 5, 3, 5)
                .add(util.select().fromTo(15, 2, 1, 19, 3, 5));
        Selection demoBearings = util.select().position(DEMO_A)
                .add(util.select().position(DEMO_B));

        scene.idle(10);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // 1 主方块
        scene.showText(80, "Place the Windmill Control Center controller block", "首先放置风车控制中心主方块")
                .pointAt(util.vector().blockSurface(CONTROLLER, Direction.NORTH))
                .attachKeyFrame();
        scene.world().showSection(controller, Direction.DOWN);
        scene.idle(85);

        // 2 终端一键放置
        scene.overlay().showControls(util.vector().blockSurface(CONTROLLER, Direction.NORTH), Pointing.LEFT, 45)
                .rightClick()
                .withItem(GTItems.TERMINAL.asStack())
                .whileSneaking();
        scene.showText(80, "Sneak-right-click it with a terminal to place the whole structure",
                "拿着终端蹲下右键，一键放置整个结构")
                .attachKeyFrame();
        scene.world().showSection(shell, Direction.DOWN);
        scene.idle(85);

        // 3 屋顶轴承与转子
        scene.showText(90,
                "The structure carries its own windmill bearing; the chassis and wool above it are the rotor",
                "结构自带风车轴承，它上方的线性底盘与羊毛构成转子")
                .pointAt(util.vector().topOf(BEARING))
                .attachKeyFrame();
        ElementLink<WorldSectionElement> rotorLink = scene.world().showIndependentSection(rotor, Direction.DOWN);
        scene.world().configureCenterOfRotation(rotorLink, util.vector().centerOf(BEARING.above()));
        scene.idle(95);

        // 4 转子按输出转速旋转
        scene.showText(110,
                "Once formed, the controller drives the rotor: speed = sqrt((512 + windmill stress) * counted / 512), capped at 256 RPM",
                "成型后控制中心会驱动转子旋转：转速 = √((512 + 风车总应力) × 计入数量 ÷ 512)，上限 256 RPM")
                .pointAt(util.vector().centerOf(BEARING.above()))
                .attachKeyFrame();
        scene.world().rotateSection(rotorLink, 0, 360, 0, 320);
        scene.idle(115);

        // 5 输入仓与润滑油
        scene.world().setBlock(LUBRICANT_HATCH, GTMachines.FLUID_IMPORT_HATCH[GTValues.LV].defaultBlockState(), true);
        scene.showUI(LUBRICANT_HATCH_UI).at(util.vector().topOf(LUBRICANT_HATCH))
                .forMachine(LUBRICANT_HATCH)
                .tank(0)
                .withFluid(new FluidStack(GTMaterials.Lubricant.getFluid(), 1000), 20)
                .show(200);
        scene.showText(110,
                "Mount a fluid input hatch on any brass casing slot and feed it lubricant (25 mB every 10 s) - the lubricant sits in the hatch's own UI",
                "在任意黄铜机壳位安装输入仓并通入润滑油（每 10 秒消耗 25 mB）—— 润滑油就存在这个仓室自己的界面里")
                .pointAt(util.vector().blockSurface(LUBRICANT_HATCH, Direction.NORTH))
                .attachKeyFrame();
        scene.idle(230);

        // 6 应力输出仓
        for (BlockPos box : OUTPUT_BOXES) {
            scene.world().setBlock(box, CTPPMachines.KINETIC_OUTPUT_BOX[GTValues.MV].defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.WEST), true);
        }
        scene.overlay().showOutline(PonderPalette.RED, "kinetic_output", outputWall, 80);
        scene.showText(105,
                "Kinetic output boxes export the stress: every tier carries 4 times the stress of the tier below, and too few boxes cannot export everything",
                "应力输出仓负责导出应力：每提升一级是上一级的 4 倍，数量不足时应力无法完全导出")
                .pointAt(util.vector().blockSurface(OUTPUT_BOX_FRONT, Direction.WEST))
                .attachKeyFrame();
        scene.idle(110);

        // 7 机械升级仓决定计入上限
        scene.world().setBlock(UPGRADE_BUS, CTPPMachines.MECHANICAL_UPGRADE_BUS[GTValues.LV].defaultBlockState(),
                true);
        scene.showText(100,
                "A mechanical upgrade bus raises the mechanical tier: the controller counts 4 + 4 x tier windmills",
                "机械升级仓提高机械等级：可统计的风车上限为 4 + 4 × 机械等级")
                .pointAt(util.vector().blockSurface(UPGRADE_BUS, Direction.NORTH))
                .attachKeyFrame();
        scene.idle(105);

        // 8 周围的风车轴承
        scene.world().showSection(demoBearings, Direction.DOWN);
        ElementLink<WorldSectionElement> demoALink = scene.world().showIndependentSection(
                util.select().fromTo(1, 2, 1, 5, 3, 5), Direction.DOWN);
        scene.world().configureCenterOfRotation(demoALink, util.vector().centerOf(3, 2, 3));
        ElementLink<WorldSectionElement> demoBLink = scene.world().showIndependentSection(
                util.select().fromTo(15, 2, 1, 19, 3, 5), Direction.DOWN);
        scene.world().configureCenterOfRotation(demoBLink, util.vector().centerOf(17, 2, 3));
        scene.showText(115,
                "Place windmill bearings within 32 blocks and start them: each one contributes spin speed x 512 su",
                "在主方块 32 格半径内放置并启动风车轴承：每个轴承按 转速 × 512 su 计入")
                .attachKeyFrame();
        scene.idle(120);

        // 9 启动风车 + 数量即倍率
        scene.overlay().showControls(util.vector().topOf(DEMO_A), Pointing.LEFT, 45).rightClick();
        scene.world().rotateSection(demoALink, 0, 360, 0, 320);
        scene.world().rotateSection(demoBLink, 0, 360, 0, 320);
        scene.overlay().showOutline(PonderPalette.GREEN, "windmills", demoSails, 90);
        scene.showText(110,
                "The stress output is the counted bearings times the stress of each bearing - more windmills means a bigger multiplier",
                "总应力输出 = 计入的轴承数 × 每个轴承的应力 —— 风车越多，倍率越高")
                .attachKeyFrame();
        scene.idle(115);

        // 10 冲突
        scene.overlay().showOutline(PonderPalette.RED, "conflict", util.select().fromTo(5, 1, 7, 15, 5, 17), 90);
        scene.showText(100, "Another windmill control center within 64 blocks forces the stress output to zero",
                "64 格内存在另一个风车控制中心时，应力输出直接归零")
                .pointAt(util.vector().blockSurface(CONTROLLER, Direction.NORTH))
                .attachKeyFrame();
        scene.idle(105);

        scene.markAsFinished();
    }
}
