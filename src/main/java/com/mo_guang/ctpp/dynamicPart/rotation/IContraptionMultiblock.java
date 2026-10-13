package com.mo_guang.ctpp.dynamicPart.rotation;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.mo_guang.ctpp.api.pattern.StaticBlockPattern;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public interface IContraptionMultiblock<T extends SimpleRotatingContraptionEntity> extends IMultiController {

    Map<Integer, T> assemble(BlockPos pivot);

    List<T> getContraptionEntity();

    void setContraptionEntity(List<T> entities);

    default boolean isAttachedTo(AbstractContraptionEntity contraption) {
        return getContraptionEntity() != null && getContraptionEntity().contains(contraption);
    }

    default void attach(T contraption) {
        if (getContraptionEntity().isEmpty()) {
            setContraptionEntity(List.of(contraption));
        } else {
            List<T> rotatingEntity = new ArrayList<>(getContraptionEntity());
            rotatingEntity.add(contraption);
            setContraptionEntity(rotatingEntity);
        }
        self().holder.notifyBlockUpdate();
    }

    default BlockPos getBlockPosition() {
        return self().getPos();
    }

    @Nullable
    default BlockPos getAssemblyPivot() {
        return null;
    }

    default void onDebugAssembled() {}

    /**
     * After chunk reload, find existing entities that belong to this controller
     * and reattach them. This handles the case where the entity list was lost
     * during unload.
     */
    @SuppressWarnings("unchecked")
    default void findAndReattachEntities() {
        if (self().getLevel() == null || self().getLevel().isClientSide) return;
        // A partial entity set is possible when several group chunks load in different ticks. Prune stale handles and
        // search for every missing sibling instead of treating a non-empty list as complete.
        List<T> attached = getContraptionEntity() == null ? new ArrayList<>() :
                new ArrayList<>(getContraptionEntity());
        attached.removeIf(entity -> entity == null || entity.isRemoved() ||
                entity.controllerPos == null || !entity.controllerPos.equals(self().getPos()));
        setContraptionEntity(attached);

        BlockPos pos = self().getPos();
        // Search in a 32-block radius for entities that reference this controller
        AABB searchBox = new AABB(pos).inflate(32);
        for (Entity entity : self().getLevel().getEntitiesOfClass(SimpleRotatingContraptionEntity.class, searchBox)) {
            T srEntity = (T) entity;
            if (srEntity.controllerPos != null && srEntity.controllerPos.equals(pos)) {
                // Found an entity that belongs to us — reattach
                if (!attached.contains(srEntity)) {
                    attached.add(srEntity);
                }
                if (!srEntity.isRunning()) {
                    srEntity.setRunning(true);
                }
            }
        }
    }

    /** Exact evidence that every dynamic pattern group still has its controller-owned contraption entity. */
    default boolean hasCompleteAttachedContraption(int expectedGroups) {
        List<T> current = getContraptionEntity();
        if (current != null && current.size() == expectedGroups &&
                current.stream()
                        .allMatch(entity -> entity != null && !entity.isRemoved() && entity.controllerPos != null &&
                                entity.controllerPos.equals(self().getPos()))) {
            return true;
        }
        findAndReattachEntities();
        return getContraptionEntity() != null && getContraptionEntity().size() == expectedGroups;
    }

    /**
     * Whether absence of an entity at the fixed assembly pivot is authoritative. This performs only non-loading
     * checks; a FULL chunk which has not reached entity-ticking status remains unavailable.
     */
    default boolean isAssemblyPivotEntityTicking() {
        BlockPos pivot = getAssemblyPivot();
        if (pivot == null || !(self().getLevel() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return serverLevel.getChunkSource().getChunkNow(pivot.getX() >> 4, pivot.getZ() >> 4) != null &&
                serverLevel.isPositionEntityTicking(pivot) &&
                serverLevel.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pivot));
    }

    @Override
    default boolean shouldIgnoreChange(BlockPos pos, BlockState state) {
        return shouldIgnoreContraptionChange(pos, state);
    }

    /**
     * Keeps blocks moved into a rotating contraption from invalidating the source
     * multiblock while the contraption is being assembled or disassembled.
     */
    default boolean shouldIgnoreContraptionChange(BlockPos pos, BlockState state) {
        var dynamicPositions = StaticBlockPattern.getCachedDynamicPositions(getMultiblockState());
        if (dynamicPositions != null) {
            // getPattern() invokes a factory that builds the entire pattern, so even obtaining it on every block
            // notification would make a large contraption's assembly quadratic.
            return dynamicPositions.contains(pos.asLong());
        }
        if (this.getPattern() instanceof StaticBlockPattern staticBlockPattern) {
            return staticBlockPattern.isDynamicPosition(getMultiblockState(), pos);
        }
        return false;
    }

    /**
     * Default helper to assemble rotating contraptions from a StaticBlockPattern dynamic part.
     * Implementations can call this to avoid duplicating assembly logic.
     */
    @SuppressWarnings("unchecked")
    default Map<Integer, T> assembleFromPattern(BlockPos pivot) {
        return assembleFromPattern(pivot, null);
    }

    /**
     * Assemble dynamic parts with bounds expanded only around their fixed
     * rotation axis. Passing null keeps the all-axis bounds for contraptions that
     * can rotate around more than one axis.
     */
    @SuppressWarnings("unchecked")
    default Map<Integer, T> assembleFromPattern(BlockPos pivot, Direction.Axis rotationAxis) {
        if (self().getLevel() instanceof TrackedDummyWorld) return null;
        if (self().getLevel().isClientSide) return null;
        Map<Integer, T> ce = new HashMap<>();
        var pattern = self().getDefinition().getPatternFactory().get();
        if (pattern instanceof StaticBlockPattern staticBlockPattern) {
            Map<Integer, List<BlockPos>> dynamicPart = staticBlockPattern.getDynamicPart(self().getMultiblockState());
            for (var entry : dynamicPart.entrySet()) {
                int group = entry.getKey();
                var part = entry.getValue().stream()
                        .filter(pos -> !self().getLevel().getBlockState(pos).isAir())
                        .toList();
                if (part.isEmpty()) continue;
                SimpleRotatingContraption contraption = new SimpleRotatingContraption(part, pivot, rotationAxis);
                contraption.assemble(this.self().getLevel(), self().getPos());
                contraption.removeBlocksFromWorld(this.self().getLevel(), BlockPos.ZERO);
                SimpleRotatingContraptionEntity contraptionEntity = SimpleRotatingContraptionEntity
                        .create(self().getLevel(), contraption, this, pivot.getCenter());
                contraptionEntity.setPos(pivot.getX(), pivot.getY(), pivot.getZ());
                this.self().getLevel().addFreshEntity(contraptionEntity);
                ce.put(group, (T) contraptionEntity);
            }
            return ce;
        }
        return null;
    }

    /**
     * Helper: assemble rotating contraptions and attach them to this controller.
     * Will only create entities on server and non-dummy worlds, and only if no rotating entities exist yet.
     */
    default void createAndAttachRotatingEntities(BlockPos pivot) {
        createAndAttachRotatingEntities(pivot, null);
    }

    /**
     * Assemble and attach rotating entities with an optional fixed rotation
     * axis. A null axis retains the conservative behavior for dynamic-axis
     * contraptions.
     */
    default void createAndAttachRotatingEntities(BlockPos pivot, Direction.Axis rotationAxis) {
        if (self().getLevel() instanceof TrackedDummyWorld) return;
        if (self().getLevel().isClientSide) return;
        if (getContraptionEntity() == null) setContraptionEntity(new ArrayList<>());
        if (!getContraptionEntity().isEmpty()) return;
        Map<Integer, T> map = assembleFromPattern(pivot, rotationAxis);
        if (map != null && !map.isEmpty()) {
            setContraptionEntity(new ArrayList<>(map.values()));
        }
    }

    /**
     * Helper: disassemble and clear all attached rotating entities.
     */
    default void clearAndDisassembleRotatingEntities() {
        if (getContraptionEntity() != null && !getContraptionEntity().isEmpty()) {
            getContraptionEntity().forEach(entity -> {
                if (entity != null) entity.disassemble();
            });
        }
        setContraptionEntity(new ArrayList<>());
    }
}
