package dev.elsebase.portal;

import dev.elsebase.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;

/** Direct counterpart tickets derived only from independent roots, excluding transient and own mirror tickets. */
public final class MirrorLoading {
    public static final TicketType<Long> MIRROR = TicketType.create("elsebase_mirror", Long::compare, 60);
    private record Target(ServerLevel level, ChunkPos chunk) {}
    private record Root(int x, int z, int reach) {}
    private static final Set<Target> ACTIVE = new HashSet<>();
    private MirrorLoading() {}

    public static void clear() {
        for (Target t : ACTIVE) t.level().getChunkSource().removeRegionTicket(MIRROR, t.chunk(), 2, t.chunk().toLong());
        ACTIVE.clear();
    }

    public static void tick(MinecraftServer server) {
        Map<ServerLevel, Map<Long, List<Root>>> roots = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            Map<Long, List<Root>> buckets = new HashMap<>();
            // Access transformer exposes the pinned vanilla ticket map read-only; never mutate it.
            var tickets = level.getChunkSource().chunkMap.getDistanceManager().tickets;
            for (var entry : tickets.long2ObjectEntrySet()) {
                int minimum = 34;
                for (Ticket<?> ticket : entry.getValue()) {
                    var type = ticket.getType();
                    if (type != MIRROR && type != TicketType.UNKNOWN && type != TicketType.POST_TELEPORT)
                        minimum = Math.min(minimum, ticket.getTicketLevel());
                }
                if (minimum <= 33) {
                    ChunkPos pos = new ChunkPos(entry.getLongKey());
                    var root = new Root(pos.x, pos.z, Math.min(33, 33 - minimum));
                    buckets.computeIfAbsent(ChunkPos.asLong(pos.x >> 5, pos.z >> 5), k -> new ArrayList<>()).add(root);
                }
            }
            roots.put(level, buckets);
        }
        Set<Target> desired = new LinkedHashSet<>();
        int limit = Settings.MIRROR_LIMIT.get();
        for (PortalPair pair : WorldState.get(server).pairs.values()) {
            for (Endpoint source : List.of(pair.inner(), pair.external())) {
                Endpoint opposite = source.inner() ? pair.external() : pair.inner();
                ServerLevel level = server.getLevel(source.dimension()), other = server.getLevel(opposite.dimension());
                if (level == null || other == null) continue;
                ChunkPos pos = new ChunkPos(source.position());
                if (normal(roots.get(level), pos) && desired.size() < limit) desired.add(new Target(other, new ChunkPos(opposite.position())));
            }
        }
        for (Target t : ACTIVE) if (!desired.contains(t)) t.level().getChunkSource().removeRegionTicket(MIRROR, t.chunk(), 2, t.chunk().toLong());
        for (Target t : desired) t.level().getChunkSource().addRegionTicket(MIRROR, t.chunk(), 2, t.chunk().toLong());
        ACTIVE.clear(); ACTIVE.addAll(desired);
    }
    private static boolean normal(Map<Long, List<Root>> buckets, ChunkPos pos) {
        for (int x = (pos.x - 33) >> 5; x <= (pos.x + 33) >> 5; x++)
            for (int z = (pos.z - 33) >> 5; z <= (pos.z + 33) >> 5; z++)
                for (Root root : buckets.getOrDefault(ChunkPos.asLong(x, z), List.of()))
                    if (Math.max(Math.abs(root.x() - pos.x), Math.abs(root.z() - pos.z)) <= root.reach()) return true;
        return false;
    }
}
