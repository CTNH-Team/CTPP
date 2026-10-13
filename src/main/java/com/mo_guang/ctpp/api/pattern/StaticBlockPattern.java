package com.mo_guang.ctpp.api.pattern;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import com.mo_guang.ctpp.dynamicPart.rotation.IContraptionMultiblock;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StaticBlockPattern extends BlockPattern {

    private static final String DYNAMIC_POSITIONS = "ctppDynamicPositions";

    protected final boolean[][][] staticBlockMatches;
    protected final int[][][] dynamicBlockMatches;
    private final int dynamicGroupCount;

    public StaticBlockPattern(TraceabilityPredicate[][][] predicatesIn, RelativeDirection[] structureDir,
                              int[][] aisleRepetitions, int[] centerOffset,
                              boolean[][][] staticPredicates, int[][][] dynamicPredicates) {
        super(predicatesIn, structureDir, aisleRepetitions, centerOffset);
        this.staticBlockMatches = staticPredicates;
        this.dynamicBlockMatches = dynamicPredicates;
        var dynamicGroups = new IntOpenHashSet();
        for (int c = 0; c < staticPredicates.length; c++) {
            for (int b = 0; b < staticPredicates[c].length; b++) {
                for (int a = 0; a < staticPredicates[c][b].length; a++) {
                    if (!staticPredicates[c][b][a]) {
                        dynamicGroups.add(dynamicPredicates[c][b][a]);
                    }
                }
            }
        }
        this.dynamicGroupCount = dynamicGroups.size();
    }

    @Override
    public boolean checkPatternAt(MultiblockState worldState, boolean savePredicate) {
        IMultiController controller = worldState.getController();
        if (controller == null) {
            if (!worldState.world.isLoaded(worldState.controllerPos)) {
                worldState.setError(MultiblockState.UNLOAD_ERROR);
            } else {
                worldState.setError(new PatternStringError("no controller found"));
            }
            return false;
        }
        BlockPos centerPos = controller.self().getPos();
        Direction frontFacing = controller.self().getFrontFacing();
        Direction[] facings = controller.hasFrontFacing() ? new Direction[] { frontFacing } :
                new Direction[] { Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST };
        Direction upwardsFacing = controller.self().getUpwardsFacing();
        boolean allowsFlip = controller.self().allowFlip();
        boolean preserveOwnedFlip = allowsFlip && controller.isStructureFormedSnapshot();
        boolean preferredFlip = preserveOwnedFlip && controller.isStructureFlippedSnapshot();
        boolean sawUnloadedPosition = false;
        for (Direction direction : facings) {
            if (checkPatternAt(worldState, centerPos, direction, upwardsFacing, preferredFlip, savePredicate)) {
                return true;
            }
            sawUnloadedPosition |= worldState.error == MultiblockState.UNLOAD_ERROR;
            if (allowsFlip) {
                if (preserveOwnedFlip && sawUnloadedPosition) {
                    worldState.setError(MultiblockState.UNLOAD_ERROR);
                    return false;
                }
                if (checkPatternAt(worldState, centerPos, direction, upwardsFacing, !preferredFlip, savePredicate)) {
                    return true;
                }
                sawUnloadedPosition |= worldState.error == MultiblockState.UNLOAD_ERROR;
                if (sawUnloadedPosition) {
                    worldState.setError(MultiblockState.UNLOAD_ERROR);
                }
                return false;
            }
        }
        if (sawUnloadedPosition) {
            worldState.setError(MultiblockState.UNLOAD_ERROR);
        }
        return false;
    }

    public Map<Integer, List<BlockPos>> getDynamicPart(MultiblockState worldState) {
        IMultiController controller = worldState.getController();
        BlockPos centerPos = controller.self().getPos();
        Direction frontFacing = controller.self().getFrontFacing();
        Direction upwardsFacing = controller.self().getUpwardsFacing();
        // determine whether the structure is mirrored. Prefer the pattern match result
        // (worldState.neededFlip) but fall back to controller state if present.
        boolean isFlipped = worldState.isNeededFlip() || controller.self().isFlipped();
        Map<Integer, List<BlockPos>> parts = new HashMap<>();
        for (int c = 0; c < this.fingerLength; c++) {
            for (int b = 0; b < this.thumbLength; b++) {
                for (int a = 0; a < this.palmLength; a++) {
                    if (staticBlockMatches[c][b][a]) continue;
                    int relativeX = a - centerOffset[0];
                    int relativeY = b - centerOffset[1];
                    int relativeZ = c - centerOffset[2];
                    var position = setActualRelativeOffset(relativeX, relativeY, relativeZ, frontFacing,
                            upwardsFacing, isFlipped)
                            .offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                    parts.computeIfAbsent(dynamicBlockMatches[c][b][a], k -> new ArrayList<>()).add(position);
                }
            }
        }
        return parts;
    }

    /**
     * Tests the geometry cached by the last pattern scan. Contraption assembly produces many block updates in a
     * burst, so rebuilding every dynamic group and linearly searching every list for each update makes large static
     * patterns quadratic. The fallback only serves legacy callers whose context predates this cache.
     */
    public boolean isDynamicPosition(MultiblockState worldState, BlockPos pos) {
        LongSet positions = getCachedDynamicPositions(worldState);
        if (positions == null) {
            positions = new LongOpenHashSet();
            for (List<BlockPos> group : getDynamicPart(worldState).values()) {
                for (BlockPos groupPos : group) {
                    positions.add(groupPos.asLong());
                }
            }
            worldState.getMatchContext().set(DYNAMIC_POSITIONS, positions);
        }
        return positions.contains(pos.asLong());
    }

    /** The scan-owned lookup; callers must not mutate it. Null means no static pattern has populated this context. */
    @Nullable
    public static LongSet getCachedDynamicPositions(MultiblockState worldState) {
        return worldState.getMatchContext().get(DYNAMIC_POSITIONS);
    }

    @Override
    public boolean checkPatternAt(MultiblockState worldState, BlockPos centerPos, Direction frontFacing,
                                  Direction upwardsFacing, boolean isFlipped, boolean savePredicate) {
        IMultiController controller = worldState.getController();
        boolean findFirstAisle = false;
        int minZ = -centerOffset[4];
        worldState.clean();
        boolean skipAssembledDynamic = false;
        if (controller.isStructureFormedSnapshot() && dynamicGroupCount > 0 &&
                controller instanceof IContraptionMultiblock<?> contraptionController) {
            if (contraptionController.hasCompleteAttachedContraption(dynamicGroupCount)) {
                skipAssembledDynamic = true;
            } else if (!contraptionController.isAssemblyPivotEntityTicking()) {
                // Absence is not evidence while the chunk which owns the contraption entity is unavailable or has not
                // reached entity-ticking status. Keep the formed snapshot pending and retry after loading settles.
                worldState.setError(MultiblockState.UNLOAD_ERROR);
                return false;
            }
            // If the pivot is fully ticking and no complete entity set exists, validate the original blocks. A
            // disassembled structure can then reform; missing entities plus missing source blocks is definitive.
        }
        PatternMatchContext matchContext = worldState.getMatchContext();
        matchContext.getOrCreate(DYNAMIC_POSITIONS, LongOpenHashSet::new);
        Object2IntMap<SimplePredicate> globalCount = worldState.getGlobalCount();
        Object2IntMap<SimplePredicate> layerCount = worldState.getLayerCount();
        // Checking aisles
        for (int c = 0, z = minZ++, r; c < this.fingerLength; c++) {
            // Checking repeatable slices
            int validRepetitions = 0;
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset[3]); r++) {
                // Checking single slice
                layerCount.clear();

                for (int b = 0, y = -centerOffset[1]; b < this.thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < this.palmLength; a++, x++) {
                        worldState.setError(null);
                        TraceabilityPredicate declaredPredicate = blockMatches[c][b][a];
                        boolean dynamicPosition = !staticBlockMatches[c][b][a];
                        TraceabilityPredicate predicate = skipAssembledDynamic && dynamicPosition ?
                                Predicates.any() : declaredPredicate;
                        BlockPos pos = setActualRelativeOffset(x, y, z, frontFacing, upwardsFacing, isFlipped)
                                .offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        if (!worldState.update(pos, predicate)) {
                            return false;
                        }
                        if (dynamicPosition) {
                            matchContext.getOrCreate(DYNAMIC_POSITIONS, LongOpenHashSet::new).add(pos.asLong());
                        }
                        // Dynamic blocks are replaced by contraption entities after formation. Continue validating
                        // them as ANY, but retain their declared predicate in the confirmed geometry so chunk-unload
                        // preflight and reverse chunk indexing still cover the complete owned structure.
                        if (declaredPredicate.addCache() || dynamicPosition) {
                            worldState.addPosCache(pos, declaredPredicate);
                            if (savePredicate) {
                                matchContext.getOrCreate("predicates", HashMap::new).put(pos, declaredPredicate);
                            }
                        }
                        boolean canPartShared = true;
                        if (worldState.getTileEntity() instanceof IMachineBlockEntity machineBlockEntity &&
                                machineBlockEntity.getMetaMachine() instanceof IMultiPart part) { // add detected parts
                            if (!predicate.isAny()) {
                                if (part.isFormed() && !part.canShared() &&
                                        !part.hasController(controller)) { // check part can be shared
                                    canPartShared = false;
                                    worldState.setError(new PatternStringError("multiblocked.pattern.error.share"));
                                } else {
                                    matchContext.getOrCreate("parts", HashSet::new).add(part);
                                }
                            }
                        }
                        if (worldState.getBlockState().getBlock() instanceof ActiveBlock) {
                            matchContext.getOrCreate("vaBlocks", LongOpenHashSet::new)
                                    .add(worldState.getPos().asLong());
                        }
                        // StaticBlockPattern overrides BlockPattern's scan method, so CTPP's BlockPattern mixin does
                        // not run here. Collect these ownership surfaces explicitly before the predicate test.
                        if (!dynamicPosition && worldState.getBlockState().getBlock() instanceof KineticBlock) {
                            matchContext.getOrCreate("roBlocks", LongOpenHashSet::new)
                                    .add(worldState.getPos().asLong());
                        }
                        if (!dynamicPosition && worldState.getBlockState().getBlock() instanceof BlazeBurnerBlock) {
                            matchContext.getOrCreate("bbBlocks", LongOpenHashSet::new)
                                    .add(worldState.getPos().asLong());
                        }
                        if (!predicate.test(worldState) || !canPartShared) { // matching failed
                            if (findFirstAisle) {
                                if (r < aisleRepetitions[c][0]) {// retreat to see if the first aisle can start later
                                    r = c = 0;
                                    z = minZ++;
                                    matchContext.reset();
                                    findFirstAisle = false;
                                }
                            } else {
                                z++;// continue searching for the first aisle
                            }
                            continue loop;
                        }
                    }
                }
                findFirstAisle = true;
                z++;

                // Check layer-local matcher predicate
                for (var entry : layerCount.object2IntEntrySet()) {
                    if (entry.getIntValue() < entry.getKey().minLayerCount) {
                        worldState.setError(new SinglePredicateError(entry.getKey(), 3));
                        return false;
                    }
                }
                validRepetitions++;
            }
            // Repetitions out of range
            if (r < aisleRepetitions[c][0] || worldState.hasError() || !findFirstAisle) {
                if (!worldState.hasError()) {
                    worldState.setError(new PatternError());
                }
                return false;
            }

            // finished checking the aisle, so store the repetitions
            formedRepetitionCount[c] = validRepetitions;
        }

        // Check count matches amount
        for (var entry : globalCount.object2IntEntrySet()) {
            if (entry.getIntValue() < entry.getKey().minCount) {
                worldState.setError(new SinglePredicateError(entry.getKey(), 1));
                return false;
            }
        }

        worldState.setError(null);
        worldState.setNeededFlip(isFlipped);
        return true;
    }

    private BlockPos setActualRelativeOffset(int x, int y, int z, Direction facing, Direction upwardsFacing,
                                             boolean isFlipped) {
        int[] c0 = new int[] { x, y, z }, c1 = new int[3];
        if (facing == Direction.UP || facing == Direction.DOWN) {
            Direction of = facing == Direction.DOWN ? upwardsFacing : upwardsFacing.getOpposite();
            for (int i = 0; i < 3; i++) {
                switch (structureDir[i].getActualDirection(of)) {
                    case UP -> c1[1] = c0[i];
                    case DOWN -> c1[1] = -c0[i];
                    case WEST -> c1[0] = -c0[i];
                    case EAST -> c1[0] = c0[i];
                    case NORTH -> c1[2] = -c0[i];
                    case SOUTH -> c1[2] = c0[i];
                }
            }
            int xOffset = upwardsFacing.getStepX();
            int zOffset = upwardsFacing.getStepZ();
            int tmp;
            if (xOffset == 0) {
                tmp = c1[2];
                c1[2] = zOffset > 0 ? c1[1] : -c1[1];
                c1[1] = zOffset > 0 ? -tmp : tmp;
            } else {
                tmp = c1[0];
                c1[0] = xOffset > 0 ? c1[1] : -c1[1];
                c1[1] = xOffset > 0 ? -tmp : tmp;
            }
            if (isFlipped) {
                if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
                    c1[0] = -c1[0]; // flip X-axis
                } else {
                    c1[2] = -c1[2]; // flip Z-axis
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                switch (structureDir[i].getActualDirection(facing)) {
                    case UP -> c1[1] = c0[i];
                    case DOWN -> c1[1] = -c0[i];
                    case WEST -> c1[0] = -c0[i];
                    case EAST -> c1[0] = c0[i];
                    case NORTH -> c1[2] = -c0[i];
                    case SOUTH -> c1[2] = c0[i];
                }
            }
            if (upwardsFacing == Direction.WEST || upwardsFacing == Direction.EAST) {
                int xOffset = upwardsFacing == Direction.EAST ? facing.getClockWise().getStepX() :
                        facing.getClockWise().getOpposite().getStepX();
                int zOffset = upwardsFacing == Direction.EAST ? facing.getClockWise().getStepZ() :
                        facing.getClockWise().getOpposite().getStepZ();
                int tmp;
                if (xOffset == 0) {
                    tmp = c1[2];
                    c1[2] = zOffset > 0 ? -c1[1] : c1[1];
                    c1[1] = zOffset > 0 ? tmp : -tmp;
                } else {
                    tmp = c1[0];
                    c1[0] = xOffset > 0 ? -c1[1] : c1[1];
                    c1[1] = xOffset > 0 ? tmp : -tmp;
                }
            } else if (upwardsFacing == Direction.SOUTH) {
                c1[1] = -c1[1];
                if (facing.getStepX() == 0) {
                    c1[0] = -c1[0];
                } else {
                    c1[2] = -c1[2];
                }
            }
            if (isFlipped) {
                if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
                    if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                        c1[0] = -c1[0]; // flip X-axis
                    } else {
                        c1[2] = -c1[2]; // flip Z-axis
                    }
                } else {
                    c1[1] = -c1[1]; // flip Y-axis
                }
            }
        }
        return new BlockPos(c1[0], c1[1], c1[2]);
    }
}
