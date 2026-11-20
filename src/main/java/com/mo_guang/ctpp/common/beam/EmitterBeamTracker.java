package com.mo_guang.ctpp.common.beam;

import com.gregtechceu.gtceu.common.network.GTNetwork;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mo_guang.ctpp.CTPP;
import com.mo_guang.ctpp.network.packet.DelEmitterBeamPacket;
import com.mo_guang.ctpp.network.packet.SetEmitterBeamPacket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side global beam tracking: beams live independently from their emitter's visibility.
 * Players watching any loaded chunk the beam passes through receive beam data; updates are
 * re-sent on change. Watcher matching is pure ray math ({@link BeamChunkIndex}), so arbitrarily
 * long beams cost the same as short ones.
 *
 * Orphan failsafe: a live emitter heartbeats its beam every tick (setBeam / touchBeam). The
 * periodic sweep removes beams whose heartbeat stopped — e.g. when the emitter was destroyed by
 * an explosion without running its removal hooks — so stray beams disappear within a few seconds
 * instead of lingering forever.
 */
@Mod.EventBusSubscriber(modid = CTPP.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmitterBeamTracker {

    /** A beam with no heartbeat for this long is considered orphaned and swept. Placeholder; tweak freely. */
    private static final long BEAM_TIMEOUT_TICKS = 100;
    private static final long SWEEP_INTERVAL_TICKS = 20;

    private static final Map<ResourceKey<Level>, Map<Integer, Entry>> BEAMS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<ServerPlayer, Set<ChunkPos>>> WATCHERS = new HashMap<>();
    private static int nextId = 1;
    private static long sweepClock = 0;

    /** A tracked beam plus the game time of its last emitter heartbeat. */
    private record Entry(EmitterBeam beam, long lastSeen) {}

    private EmitterBeamTracker() {}

    public static int newBeamId() {
        return nextId++;
    }

    /** Insert or update a beam; viewers of covered chunks get the new state. */
    public static void setBeam(ServerLevel level, int id, List<Vec3> points, long voltage, long amps, int tier) {
        var beam = new EmitterBeam(id, points, voltage, amps, tier);
        BEAMS.computeIfAbsent(level.dimension(), d -> new HashMap<>())
                .put(id, new Entry(beam, level.getGameTime()));
        var packet = new SetEmitterBeamPacket(id, level.dimension(), beam);
        Set<ChunkPos> covered = BeamChunkIndex.coveredLoadedChunks(level, beam);
        for (var player : level.players()) {
            if (watchesAnyOf(player, covered)) {
                GTNetwork.sendToPlayer(player, packet);
            }
        }
    }

    /** Heartbeat for an unchanged beam: keeps the orphan sweep from collecting a live beam. */
    public static void touchBeam(ServerLevel level, int id) {
        var beams = BEAMS.get(level.dimension());
        if (beams == null) return;
        var entry = beams.get(id);
        if (entry != null) beams.put(id, new Entry(entry.beam(), level.getGameTime()));
    }

    public static void removeBeam(ServerLevel level, int id) {
        var beams = BEAMS.get(level.dimension());
        if (beams == null) return;
        var entry = beams.remove(id);
        if (entry == null) return;
        var packet = new DelEmitterBeamPacket(id, level.dimension());
        Set<ChunkPos> covered = BeamChunkIndex.coveredLoadedChunks(level, entry.beam());
        for (var player : level.players()) {
            if (watchesAnyOf(player, covered)) {
                GTNetwork.sendToPlayer(player, packet);
            }
        }
        if (beams.isEmpty()) BEAMS.remove(level.dimension());
    }

    /** Sweep orphaned beams: no heartbeat for {@link #BEAM_TIMEOUT_TICKS} means the emitter is gone. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++sweepClock < SWEEP_INTERVAL_TICKS) return;
        sweepClock = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var beams = BEAMS.get(level.dimension());
            if (beams == null || beams.isEmpty()) continue;
            long now = level.getGameTime();
            List<Integer> orphans = beams.entrySet().stream()
                    .filter(e -> now - e.getValue().lastSeen() > BEAM_TIMEOUT_TICKS)
                    .map(Map.Entry::getKey)
                    .toList();
            for (int id : orphans) {
                removeBeam(level, id);
            }
        }
    }

    /** Watch records are per-login; drop them when the player leaves. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (var dimWatchers : WATCHERS.values()) {
                dimWatchers.remove(player);
            }
        }
    }

    private static boolean watchesAnyOf(ServerPlayer player, Set<ChunkPos> covered) {
        var dimWatchers = WATCHERS.get(player.level().dimension());
        if (dimWatchers == null) return false;
        var watched = dimWatchers.get(player);
        if (watched == null) return false;
        for (ChunkPos pos : covered) {
            if (watched.contains(pos)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerLevel level = event.getLevel();
        ServerPlayer player = event.getPlayer();
        WATCHERS.computeIfAbsent(level.dimension(), d -> new HashMap<>())
                .computeIfAbsent(player, p -> new HashSet<>())
                .add(event.getPos());
        var beams = BEAMS.get(level.dimension());
        if (beams == null) return;
        for (var entry : beams.values()) {
            var beam = entry.beam();
            if (BeamChunkIndex.chunkCovered(beam, event.getPos())) {
                GTNetwork.sendToPlayer(player,
                        new SetEmitterBeamPacket(beam.id(), level.dimension(), beam));
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnWatch(ChunkWatchEvent.UnWatch event) {
        ServerLevel level = event.getLevel();
        ServerPlayer player = event.getPlayer();
        var dimWatchers = WATCHERS.get(level.dimension());
        if (dimWatchers == null) return;
        var watched = dimWatchers.get(player);
        if (watched == null) return;
        watched.remove(event.getPos());
        var beams = BEAMS.get(level.dimension());
        if (beams == null) return;
        for (var entry : beams.values()) {
            var beam = entry.beam();
            if (BeamChunkIndex.chunkCovered(beam, event.getPos()) &&
                    !watchesAnyOf(player, BeamChunkIndex.coveredLoadedChunks(level, beam))) {
                GTNetwork.sendToPlayer(player,
                        new DelEmitterBeamPacket(beam.id(), level.dimension()));
            }
        }
        if (watched.isEmpty()) dimWatchers.remove(player);
    }
}
