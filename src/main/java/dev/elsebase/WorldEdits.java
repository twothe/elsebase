package dev.elsebase;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Bounded atomic edits: validate protection, capture changes without notifications, roll back denied placements. */
public final class WorldEdits {
    public record Change(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {}
    /** Cosmetic bindings still require build permission and claim approval for each structural block. */
    public static boolean authorizeAppearance(ServerPlayer player,List<BlockPos> positions) {
        if(player.isSpectator() || !player.mayBuild() || positions.size()>256) return false;
        var level=player.serverLevel();
        for(var pos:positions) {
            if(!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player,pos)) return false;
            var state=level.getBlockState(pos);
            if(NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,state,player)).isCanceled()) return false;
        }
        return true;
    }
    public static boolean apply(ServerPlayer player, List<Change> changes) {
        return apply(player, changes, false);
    }
    /** Destructive recovery is restricted to the owner's saved anchor column; normal tools cannot opt into it. */
    public static boolean recoverAnchor(ServerPlayer player, List<Change> changes) {
        var data = dev.elsebase.portal.WorldState.get(player.server);
        var home = data.homes.get(player.getUUID());
        if (home == null || changes.size() > 3) throw new IllegalArgumentException("Invalid anchor recovery footprint");
        var marker = home.anchor();
        Set<BlockPos> seen = new HashSet<>();
        for (var change : changes) {
            var expected = change.pos().equals(marker) ? Content.ANCHOR.get().defaultBlockState()
                    : change.pos().equals(marker.above()) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : change.pos().equals(marker.below()) ? Content.structure(dev.elsebase.world.RoomLayout.Material.FLOOR) : null;
            if (!change.level().dimension().equals(Elsebase.DIMENSION) || !change.after().equals(expected) || !seen.add(change.pos()))
                throw new IllegalArgumentException("Recovery must only repair the owner's marker, headroom and floor");
            // Saved neighbors and registered doorways are never sacrificed to free another owner's spawn.
            if (data.at(Elsebase.DIMENSION,change.pos()) != null) return false;
            for (var entry : data.homes.entrySet()) if (!entry.getKey().equals(player.getUUID())
                    && (change.pos().equals(entry.getValue().anchor()) || change.pos().equals(entry.getValue().anchor().below()))) return false;
        }
        return apply(player, changes, true);
    }
    private static boolean apply(ServerPlayer player, List<Change> changes, boolean recoverBlockEntities) {
        if (changes.size() > 256) throw new IllegalArgumentException("Atomic edit exceeds one panel");
        for (Change c : changes) {
            if (!c.level().getWorldBorder().isWithinBounds(c.pos()) || c.level().isOutsideBuildHeight(c.pos())
                    || !c.level().getBlockState(c.pos()).equals(c.before())
                    || !recoverBlockEntities && c.level().getBlockEntity(c.pos()) != null && !(c.before().is(Content.PORTAL.get()) && c.level().getBlockEntity(c.pos()) instanceof dev.elsebase.portal.PortalSurface)
                    || !c.level().mayInteract(player, c.pos()) || player.isSpectator()
                    || !player.mayBuild()) return false;
            if (!c.before().isAir()
                    && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(c.level(), c.pos(), c.before(), player)).isCanceled()) return false;
        }
        List<BlockSnapshot> snapshots = new ArrayList<>();
        for (Change c : changes) snapshots.add(BlockSnapshot.create(c.level().dimension(), c.level(), c.pos(), 3));
        boolean denied = false;
        int staged = 0;
        try {
            for (Change c : changes) {
                staged++;
                // Preserve the NBT snapshot but detach the live inventory before onRemove could drop it.
                if (recoverBlockEntities) c.level().getChunkAt(c.pos()).removeBlockEntity(c.pos());
                if (!c.level().setBlock(c.pos(), c.after(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS)) {
                    rollback(changes, snapshots, staged); return false;
                }
            }
            for (int i = 0; i < changes.size(); i++) {
                Change c = changes.get(i);
                if (!c.after().isAir() && NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                        snapshots.get(i), c.level().getBlockState(c.pos().below()), player)).isCanceled()) { denied = true; break; }
            }
        } catch (RuntimeException e) {
            rollback(changes, snapshots, staged);
            Elsebase.LOGGER.error("Protection callback failed; edit rolled back for {}", player.getUUID(), e);
            throw e;
        }
        if (denied) { rollback(changes, snapshots, staged); return false; }
        for (Change c : changes) {
            c.level().sendBlockUpdated(c.pos(), c.before(), c.after(), Block.UPDATE_ALL);
            c.level().updateNeighborsAt(c.pos(), c.after().getBlock());
        }
        return true;
    }
    private static void rollback(List<Change> changes, List<BlockSnapshot> snapshots, int count) {
        for (int i = count - 1; i >= 0; i--) {
            Change c = changes.get(i);
            c.level().setBlock(c.pos(), c.before(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            // A failed set may have left the same block state with its entity detached.
            if (snapshots.get(i).getTag() != null && c.level().getBlockEntity(c.pos()) == null) {
                var restored = snapshots.get(i).recreateBlockEntity(c.level().registryAccess());
                if (restored == null) throw new IllegalStateException("Cannot restore block entity at " + c.pos());
                c.level().setBlockEntity(restored);
            } else snapshots.get(i).restoreBlockEntity(c.level(), c.pos());
        }
    }
    private WorldEdits() {}
}
