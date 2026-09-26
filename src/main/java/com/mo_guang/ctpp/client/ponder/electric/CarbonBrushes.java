package com.mo_guang.ctpp.client.ponder.electric;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import com.mo_guang.ctpp.client.ponder.CTPPPonderSceneBuilder;

public class CarbonBrushes {

    private CarbonBrushes() {}

    public static void ponder(SceneBuilder builder, SceneBuildingUtil util) {
        CTPPPonderSceneBuilder scene = new CTPPPonderSceneBuilder(builder);

        Selection magnets = util.select().fromTo(2, 1, 2, 4, 1, 2)
                .add(util.select().fromTo(2, 5, 2, 4, 5, 2))
                .add(util.select().fromTo(1, 2, 2, 1, 4, 2))
                .add(util.select().fromTo(5, 2, 2, 5, 4, 2));
        Selection allMagnets = magnets;
        scene.title("carbon_brushes", "Generate electricity with carbon brushes", "使用碳刷发电",
                "Carbon Brushes", "碳刷");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.65f);
        scene.setSceneOffsetY(-1);

        scene.idle(10);
        scene.world().showSection(util.select().layer(0), Direction.NORTH);
        scene.idle(10);
        scene.world().showSection(util.select().position(3, 3, 1), Direction.NORTH);
        scene.showText(70,
                "Carbon Brushes convert the kinetic energy collected from generator coils into electrical energy.",
                "碳刷会把发电机线圈收集到的动能转化为电能。")
                .pointAt(util.vector().blockSurface(util.grid().at(3, 3, 1), Direction.WEST))
                .attachKeyFrame();
        scene.idle(70);
        scene.world().showSection(util.select().position(3, 3, 2), Direction.NORTH);
        scene.idle(10);
        scene.showText(70,
                "Place generator coils in front of and behind the Carbon Brushes. Their shafts must point along the same line.",
                "在碳刷的前后放置发电机线圈，线圈的转轴必须与碳刷处于同一直线上。")
                .pointAt(util.vector().blockSurface(util.grid().at(3, 3, 2), Direction.WEST))
                .attachKeyFrame();
        scene.world().setKineticSpeed(util.select().position(3, 3, 1), 64);
        scene.world().setKineticSpeed(util.select().position(3, 3, 2), 64);
        scene.idle(70);
        scene.world().showSection(magnets, Direction.NORTH);
        scene.idle(10);
        scene.showText(80,
                "A coil only generates energy after magnets are placed around it. One coil has twelve magnet positions.",
                "线圈周围安装磁铁后才能发电。一个线圈周围共有十二个磁铁位置。")
                .pointAt(util.vector().blockSurface(util.grid().at(3, 5, 2), Direction.DOWN))
                .attachKeyFrame();
        scene.idle(70);
        scene.world().showSection(util.select().position(2, 3, 1), Direction.EAST);
        scene.world().showSection(util.select().position(2, 2, 1), Direction.EAST);
        scene.showText(80,
                "Connect the Carbon Brushes to the power network on either side to export and use the generated energy.",
                "从碳刷两侧接入电网，就能导出并使用产出的能量。")
                .pointAt(util.vector().blockSurface(util.grid().at(2, 3, 1), Direction.WEST))
                .attachKeyFrame();

        scene.idle(70);
        scene.showText(70,
                "Place magnetic iron or magnetic steel blocks in these twelve positions. CTPP reads their magnetic strength.",
                "在这十二个位置放置磁性铁块或磁性钢块，CTPP 会读取方块的磁力值。")
                .pointAt(util.vector().blockSurface(util.grid().at(3, 5, 2), Direction.DOWN))
                .attachKeyFrame();
        scene.idle(70);
        scene.world().showSection(util.select().fromTo(3, 3, 3, 3, 3, 9), Direction.NORTH);
        scene.world().setKineticSpeed(util.select().fromTo(3, 3, 2, 3, 3, 9), 64);
        scene.idle(10);
        scene.idle(70);
        scene.showText(80,
                "One Carbon Brush can receive kinetic energy from up to eight generator coils.",
                "一个碳刷最多可以接收八个发电机线圈的动能。")
                .attachKeyFrame();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 5, 2, 5, 1, 9), Direction.NORTH);
        scene.idle(70);
        scene.showText(80,
                "More magnets increase the coil's stress consumption, so make sure the kinetic network can supply it.",
                "磁铁越多，线圈的应力消耗越高，因此要确保机械动力网络能够承担这份负载。")
                .pointAt(util.vector().blockSurface(util.grid().at(2, 5, 2), Direction.WEST))
                .attachKeyFrame();
        scene.idle(60);
        scene.showText(80,
                "Speed also affects stress consumption. Raising the speed increases both the load and the generated energy.",
                "转速同样会影响应力消耗。提高转速会同时增加负载和发电量。")
                .pointAt(util.vector().blockSurface(util.grid().at(3, 3, 1), Direction.WEST))
                .attachKeyFrame();
        scene.world().setKineticSpeed(util.select().position(3, 3, 1), 128);
        scene.world().setKineticSpeed(util.select().fromTo(3, 3, 2, 3, 3, 9), 128);
        scene.idle(70);
        scene.showText(100,
                "Higher-tier magnets provide more strength, but they also make the coil consume more stress.",
                "更高等级的磁铁能提供更强的磁力，但也会让线圈消耗更多应力。")
                .attachKeyFrame();
        scene.idle(20);
        BlockState magneticIron = ChemicalHelper.getBlock(TagPrefix.block, GTMaterials.IronMagnetic)
                .defaultBlockState();
        scene.world().replaceBlocks(allMagnets, magneticIron, true);
        scene.idle(20);
        BlockState magneticSteel = ChemicalHelper.getBlock(TagPrefix.block, GTMaterials.SteelMagnetic)
                .defaultBlockState();
        scene.world().replaceBlocks(allMagnets, magneticSteel, true);
        scene.idle(20);
        scene.markAsFinished();
    }
}
