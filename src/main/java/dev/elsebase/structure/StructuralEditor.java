package dev.elsebase.structure;

import dev.elsebase.*;
import dev.elsebase.portal.Portals;
import dev.elsebase.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded panel queue shared by all players; every panel is revalidated atomically at commit time. */
public final class StructuralEditor {
    /** One room-owned surface. UP/DOWN are slabs; horizontal directions are wall halves. */
    public record Panel(int cellX, int cellZ, Direction side, int floor) {
        public net.minecraft.world.phys.AABB bounds(boolean restore) {
            int x = cellX * 16, z = cellZ * 16;
            if (side.getAxis() == Direction.Axis.Y) {
                int y = side == Direction.UP ? RoomLayout.ceilingAt(floor) : floor;
                int inset = restore ? 0 : 1;
                return new net.minecraft.world.phys.AABB(x+inset,y,z+inset,x+16-inset,y+1,z+16-inset);
            }
            int x0 = side == Direction.WEST ? x : side == Direction.EAST ? x+15 : x+1;
            int z0 = side == Direction.NORTH ? z : side == Direction.SOUTH ? z+15 : z+1;
            return new net.minecraft.world.phys.AABB(x0,floor+1,z0,
                    x0+(side.getAxis()==Direction.Axis.X ? 1 : 14),RoomLayout.ceilingAt(floor),
                    z0+(side.getAxis()==Direction.Axis.Z ? 1 : 14));
        }
        public List<BlockPos> positions(boolean restore) {
            var box = bounds(restore);
            List<BlockPos> positions = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed((int)box.minX,(int)box.minY,(int)box.minZ,
                    (int)box.maxX-1,(int)box.maxY-1,(int)box.maxZ-1)) positions.add(pos.immutable());
            return positions;
        }
    }
    private record Job(UUID player, Panel panel, boolean restore) {}
    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private StructuralEditor() {}
    public static void clear() { QUEUE.clear(); }

    /** Server derives the panel from current player pose, never from client-supplied coordinates. */
    public static void request(ServerPlayer player, boolean restore) {
        if (!player.level().dimension().equals(Elsebase.DIMENSION)) { Portals.message(player, "Structural editing belongs in the Backdoor."); return; }
        if (player.isSpectator()) return;
        if (QUEUE.stream().anyMatch(j -> j.player().equals(player.getUUID()))) { Portals.message(player, "Your previous edit is still queued."); return; }
        if (QUEUE.size() >= 128) { Portals.message(player, "Structural edit queue full; try again."); return; }
        Panel panel = PanelSelection.select(player.level(), player.position(), player.getEyePosition(), player.getLookAngle(), restore);
        if (panel != null) QUEUE.add(new Job(player.getUUID(), panel, restore));
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        int remaining = Settings.EDIT_BUDGET.get();
        while (!QUEUE.isEmpty()) {
            Job job = QUEUE.peek();
            int bound = job.panel().positions(job.restore()).size();
            if (remaining < bound) return;
            QUEUE.remove(); remaining -= bound;
            ServerPlayer player = server.getPlayerList().getPlayer(job.player());
            ServerLevel level = server.getLevel(Elsebase.DIMENSION);
            if (player == null || level == null || player.serverLevel() != level
                    || !level.hasChunk(job.panel().cellX(), job.panel().cellZ())
                    || !PanelSelection.withinReach(player.position(), job.panel())) continue;
            if (!(level.getChunkSource().getGenerator() instanceof RoomGenerator generator)) continue;
            Set<BlockPos> anchorFloors = new HashSet<>();
            if (!job.restore()) dev.elsebase.portal.WorldState.get(server).homes.values().forEach(home -> anchorFloors.add(home.anchor().below()));
            List<WorldEdits.Change> changes = new ArrayList<>();
            boolean blocked = false;
            for (BlockPos pos : job.panel().positions(job.restore())) {
                BlockState current = level.getBlockState(pos);
                if (job.restore()) {
                    BlockState original = job.panel().side().getAxis() != Direction.Axis.Y
                            ? Content.structure(RoomLayout.Material.WALL) : generator.original(pos.getX(), pos.getY(), pos.getZ());
                    if (!current.isAir() && !current.equals(original) && !StructuralBlock.protectedStructure(level, current)) { blocked = true; break; }
                    if (!original.equals(current)) {
                        if (!original.isAir() && !level.getEntities(null, new net.minecraft.world.phys.AABB(pos)).isEmpty()) { blocked = true; break; }
                        changes.add(new WorldEdits.Change(level, pos, current, original));
                    }
                } else if (StructuralBlock.protectedStructure(level, current)) {
                    if (level.getBlockState(pos.above()).is(Content.ANCHOR.get())
                            || anchorFloors.contains(pos)) {
                        blocked = true; break;
                    }
                    changes.add(new WorldEdits.Change(level, pos, current, Blocks.AIR.defaultBlockState()));
                }
            }
            if (blocked || !WorldEdits.apply(player, changes)) {
                QUEUE.removeIf(j -> j.player().equals(job.player()));
                Portals.message(player, "Surface obstructed or protected; edit cancelled.");
            }
        }
    }
}
