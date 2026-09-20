package dev.elsebase.portal;

import dev.elsebase.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;

/** Personal marker placement, entry support repair and online-owner chunk tickets. */
public final class Anchors {
    public static final TicketType<UUID> TICKET = TicketType.create("elsebase_anchor", UUID::compareTo);
    private record Loaded(ServerLevel level, ChunkPos chunk) {}
    private static final Map<UUID, Loaded> ACTIVE = new HashMap<>();
    private Anchors() {}

    /** Repairs only replaceable support; machines and protected construction must never be overwritten. */
    public static boolean ensure(ServerPlayer player) {
        var level = player.server.getLevel(Elsebase.DIMENSION);
        if (level == null) return false;
        var data = WorldState.get(player.server);
        var home = data.home(player.server, player.getUUID());
        BlockPos marker = home.anchor();
        track(player);
        level.getChunkAt(marker);
        var floor = marker.below();
        var current = level.getBlockState(floor);
        List<WorldEdits.Change> changes = new ArrayList<>();
        if (!current.isFaceSturdy(level, floor, Direction.UP)) {
            if (!current.canBeReplaced() || level.getBlockEntity(floor) != null) return false;
            changes.add(new WorldEdits.Change(level, floor, current, Content.structure(dev.elsebase.world.RoomLayout.Material.FLOOR)));
        }
        var state = level.getBlockState(marker);
        if (!state.is(Content.ANCHOR.get())) {
            if (!state.isAir()) return false;
            changes.add(new WorldEdits.Change(level, marker, state, Content.ANCHOR.get().defaultBlockState()));
        }
        if (!WorldEdits.apply(player, changes)) return false;
        return true;
    }

    /** Each online owner retains one ticking anchor chunk; moving or logout releases the old ticket. */
    public static void track(ServerPlayer player) {
        var home = WorldState.get(player.server).homes.get(player.getUUID());
        if (home == null) return;
        var level = player.server.getLevel(Elsebase.DIMENSION);
        if (level == null) return;
        var next = new Loaded(level, new ChunkPos(home.anchor()));
        if (next.equals(ACTIVE.get(player.getUUID()))) return;
        release(player.getUUID());
        level.getChunkSource().addRegionTicket(TICKET, next.chunk(), 2, player.getUUID());
        ACTIVE.put(player.getUUID(), next);
    }
    public static void release(UUID owner) {
        var old = ACTIVE.remove(owner);
        if (old != null) old.level().getChunkSource().removeRegionTicket(TICKET, old.chunk(), 2, owner);
    }
    public static void clear() { for (var owner : List.copyOf(ACTIVE.keySet())) release(owner); }
}
