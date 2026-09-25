package dev.elsebase.portal;

import dev.elsebase.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** One personal landing policy for portal entry, initial spawn and respawn; never changes the saved anchor. */
public final class AnchorArrival {
    private AnchorArrival() {}

    /** Prefer the anchor, then nearby footing, then a three-block claim-aware emergency repair. */
    public static Vec3 resolve(ServerPlayer player) {
        var level = player.server.getLevel(Elsebase.DIMENSION);
        if (level == null) return null;
        var marker = WorldState.get(player.server).home(player.server,player.getUUID()).anchor();
        Anchors.ensure(player); // Best-effort normal repair; an obstruction is not an entry veto.
        Vec3 preferred = Vec3.atBottomCenterOf(marker);
        if (ReturnTravel.safeStanding(player,level,preferred)) return preferred;

        // Same elevation first. No heightmap search: it could put the player on the dimension roof.
        for (int dy : new int[]{0,1,-1,2,-2}) for (int radius=0;radius<=8;radius++)
            for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
                if (Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
                var pos = marker.offset(dx,dy,dz);
                if (!level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) continue;
                level.getChunkAt(pos);
                Vec3 candidate = Vec3.atBottomCenterOf(pos);
                if (ReturnTravel.safeStanding(player,level,candidate)) return candidate;
            }

        List<WorldEdits.Change> changes = new ArrayList<>();
        var floor = marker.below();
        var support = level.getBlockState(floor);
        if (!support.isFaceSturdy(level,floor,Direction.UP) || ReturnTravel.hazardous(support))
            changes.add(new WorldEdits.Change(level,floor,support,Content.structure(dev.elsebase.world.RoomLayout.Material.FLOOR)));
        var feet = level.getBlockState(marker);
        if (!feet.is(Content.ANCHOR.get())) changes.add(new WorldEdits.Change(level,marker,feet,Content.ANCHOR.get().defaultBlockState()));
        var head = level.getBlockState(marker.above());
        if (!head.isAir()) changes.add(new WorldEdits.Change(level,marker.above(),head,Blocks.AIR.defaultBlockState()));
        if (changes.isEmpty() || !WorldEdits.recoverAnchor(player,changes)) return null;
        Elsebase.LOGGER.warn("Emergency anchor clearance for {} at {}: {}",player.getUUID(),marker,
                changes.stream().map(c -> c.pos()+" "+c.before()+" -> "+c.after()).toList());
        Portals.message(player,"elsebase.message.spawn_area_cleared");
        return ReturnTravel.safeStanding(player,level,preferred) ? preferred : null;
    }
}
