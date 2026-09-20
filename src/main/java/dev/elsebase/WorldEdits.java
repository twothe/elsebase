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
    public static boolean apply(ServerPlayer player, List<Change> changes) {
        if (changes.size() > 256) throw new IllegalArgumentException("Atomic edit exceeds one panel");
        for (Change c : changes) {
            if (!c.level().getWorldBorder().isWithinBounds(c.pos()) || c.level().isOutsideBuildHeight(c.pos())
                    || !c.level().getBlockState(c.pos()).equals(c.before())
                    || c.level().getBlockEntity(c.pos()) != null && !(c.before().is(Content.PORTAL.get()) && c.level().getBlockEntity(c.pos()) instanceof dev.elsebase.portal.PortalSurface)
                    || !c.level().mayInteract(player, c.pos()) || player.isSpectator()
                    || !player.mayBuild()) return false;
            if (!c.before().isAir()
                    && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(c.level(), c.pos(), c.before(), player)).isCanceled()) return false;
        }
        List<BlockSnapshot> snapshots = new ArrayList<>();
        for (Change c : changes) {
            snapshots.add(BlockSnapshot.create(c.level().dimension(), c.level(), c.pos(), 3));
            if (!c.level().setBlock(c.pos(), c.after(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS)) {
                rollback(changes, snapshots.size());
                return false;
            }
        }
        boolean denied = false;
        try {
            for (int i = 0; i < changes.size(); i++) {
                Change c = changes.get(i);
                if (!c.after().isAir() && NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                        snapshots.get(i), c.level().getBlockState(c.pos().below()), player)).isCanceled()) { denied = true; break; }
            }
        } catch (RuntimeException e) {
            rollback(changes, changes.size());
            Elsebase.LOGGER.error("Protection callback failed; edit rolled back for {}", player.getUUID(), e);
            throw e;
        }
        if (denied) { rollback(changes, changes.size()); return false; }
        for (Change c : changes) {
            c.level().sendBlockUpdated(c.pos(), c.before(), c.after(), Block.UPDATE_ALL);
            c.level().updateNeighborsAt(c.pos(), c.after().getBlock());
        }
        return true;
    }
    private static void rollback(List<Change> changes, int count) {
        for (int i = count - 1; i >= 0; i--) {
            Change c = changes.get(i);
            c.level().setBlock(c.pos(), c.before(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        }
    }
    private WorldEdits() {}
}
