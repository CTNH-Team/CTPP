package com.mo_guang.ctpp.api.pattern;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.mo_guang.ctpp.CTPP;
import com.mo_guang.ctpp.dynamicPart.rotation.IContraptionMultiblock;
import com.simibubi.create.AllBlocks;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
@GameTestHolder(CTPP.MODID)
public class StaticBlockPatternTest {

    @GameTest(template = "empty", batch = "CTPPStaticPattern")
    public static void assembledDynamicBlocksKeepGeometryAndConstantTimeChangeLookup(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true, true);
        helper.assertTrue(check(fixture), "An attached contraption could not validate its static structure");
        helper.assertTrue(fixture.state.getPredicateMap().get(fixture.center.asLong()) == fixture.dynamic,
                "The moved coordinate lost its declared predicate and chunk-unload coverage");
        LongSet rotating = fixture.state.getMatchContext().get("roBlocks");
        helper.assertTrue(rotating != null && rotating.contains(fixture.center.above().asLong()),
                "The static pattern omitted its ordinary kinetic ownership surface");
        helper.assertFalse(rotating.contains(fixture.center.asLong()),
                "A moved dynamic coordinate was incorrectly registered as an ordinary kinetic block");
        for (int i = 0; i < 4096; i++) {
            helper.assertTrue(fixture.controller.shouldIgnoreContraptionChange(fixture.center,
                    Blocks.AIR.defaultBlockState()), "The cached dynamic position was not ignored");
            helper.assertFalse(fixture.controller.shouldIgnoreContraptionChange(fixture.center.above(),
                    AllBlocks.SHAFT.getDefaultState()), "A static position was incorrectly ignored");
        }
        // The fixture throws if getPattern() is called: block-update bursts must use the cached primitive lookup.
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "CTPPStaticPattern")
    public static void missingContraptionRemainsPendingUntilEntityAbsenceIsAuthoritative(GameTestHelper helper) {
        Fixture fixture = fixture(helper, false, false);
        helper.assertFalse(check(fixture), "An unavailable contraption unexpectedly formed");
        helper.assertTrue(fixture.state.error == MultiblockState.UNLOAD_ERROR,
                "Entity IO lag was treated as a broken structure");

        fixture.entityReady.set(true);
        helper.assertFalse(check(fixture), "Missing source blocks and a missing entity unexpectedly formed");
        helper.assertTrue(fixture.state.error != MultiblockState.UNLOAD_ERROR,
                "Authoritative entity absence remained pending forever");

        helper.getLevel().setBlockAndUpdate(fixture.center, Blocks.STONE.defaultBlockState());
        helper.assertTrue(check(fixture), "An intact disassembled structure could not reform");
        helper.succeed();
    }

    private static boolean check(Fixture fixture) {
        return fixture.pattern.checkPatternAt(fixture.state, fixture.center, Direction.SOUTH, Direction.NORTH,
                false, true);
    }

    private static Fixture fixture(GameTestHelper helper, boolean attached, boolean ready) {
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(center.above(), AllBlocks.SHAFT.getDefaultState());
        var controllerRef = new AtomicReference<IMultiController>();
        var state = new MultiblockState(helper.getLevel(), center) {

            @Override
            public IMultiController getController() {
                return controllerRef.get();
            }
        };
        var entityReady = new AtomicBoolean(ready);
        var controller = (IContraptionMultiblock<?>) Proxy.newProxyInstance(
                IContraptionMultiblock.class.getClassLoader(), new Class<?>[] { IContraptionMultiblock.class },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "isStructureFormedSnapshot" -> true;
                        case "hasCompleteAttachedContraption" -> attached;
                        case "isAssemblyPivotEntityTicking" -> entityReady.get();
                        case "getMultiblockState" -> state;
                        case "getPattern" -> throw new AssertionError(
                                "A cached block update rebuilt the entire pattern");
                        default -> {
                            if (method.isDefault()) yield InvocationHandler.invokeDefault(proxy, method, args);
                            throw new AssertionError("Unexpected controller method: " + method.getName());
                        }
                    };
                });
        controllerRef.set(controller);
        var dynamic = Predicates.blocks(Blocks.STONE);
        var pattern = new StaticBlockPattern(
                new TraceabilityPredicate[][][] { { { dynamic }, { Predicates.blocks(AllBlocks.SHAFT.get()) } } },
                new RelativeDirection[] { RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT },
                new int[][] { { 1, 1 } }, new int[5], new boolean[][][] { { { false }, { true } } },
                new int[][][] { { { 0 }, { 0 } } });
        return new Fixture(center, pattern, state, controller, dynamic, entityReady);
    }

    private record Fixture(BlockPos center, StaticBlockPattern pattern, MultiblockState state,
                           IContraptionMultiblock<?> controller, TraceabilityPredicate dynamic,
                           AtomicBoolean entityReady) {}
}
