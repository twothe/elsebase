package dev.elsebase.portal;

import dev.elsebase.Elsebase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Escape is independent of physical portal blocks and inner anchor health; searches have fixed bounds. */
public final class ReturnTravel {
    private ReturnTravel() {}

    /** Normal landings must have dry, collision-free body space and nonhazardous solid support. */
    public static boolean safe(ServerPlayer player, ServerLevel level, Vec3 position) {
        var box = player.getBoundingBox().move(position.subtract(player.position()));
        return safe(player, level, position, box);
    }
    /** Personal arrivals must allow standing up, even when entering crouched, swimming or during respawn. */
    public static boolean safeStanding(ServerPlayer player, ServerLevel level, Vec3 position) {
        return safe(player, level, position, player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(position));
    }
    private static boolean safe(ServerPlayer player, ServerLevel level, Vec3 position, net.minecraft.world.phys.AABB box) {
        var feet = BlockPos.containing(position);
        if (!level.getWorldBorder().isWithinBounds(box) || box.minY < level.getMinBuildHeight()
                || box.maxY > level.getMaxBuildHeight()) return false;
        var support = level.getBlockState(feet.below());
        if (!support.isFaceSturdy(level,feet.below(),Direction.UP) || hazardous(support)) return false;
        if (!level.noCollision(player,box) || level.containsAnyLiquid(box)) return false;
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))
            if (hazardous(level.getBlockState(pos))) return false;
        return true;
    }
    static boolean hazardous(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
    }
    /** Preferred doorway side first, then a bounded neighborhood and surface columns. No terrain is cleared. */
    public static Vec3 nearby(ServerPlayer player, ServerLevel level, Vec3 preferred) {
        if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(preferred))) return null;
        level.getChunkAt(BlockPos.containing(preferred));
        if (safe(player,level,preferred)) return preferred;
        BlockPos origin = BlockPos.containing(preferred);
        for (int radius=0;radius<=8;radius++) for (int dx=-radius;dx<=radius;dx++) for (int dz=-radius;dz<=radius;dz++) {
            if (Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            BlockPos column = origin.offset(dx,0,dz);
            if (!level.getWorldBorder().isWithinBounds(column)) continue;
            level.getChunkAt(column);
            for (int distance=0;distance<=8;distance++) for (int sign : new int[]{1,-1}) {
                if (distance==0 && sign==-1) continue;
                Vec3 candidate = Vec3.atBottomCenterOf(column.above(distance*sign));
                if (safe(player,level,candidate)) return candidate;
            }
        }
        for (int dx=-8;dx<=8;dx++) for (int dz=-8;dz<=8;dz++) {
            var column = origin.offset(dx,0,dz);
            if (!level.getWorldBorder().isWithinBounds(column)) continue;
            var candidate = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column));
            if (safe(player,level,candidate)) return candidate;
        }
        return null;
    }
    /** Returns near the saved outside endpoint, falling back to the overworld spawn even without a pair. */
    public static void escape(ServerPlayer player, Endpoint endpoint, Vec3 preferred) {
        escape(player,endpoint,preferred,null);
    }
    /** A source frame identifies actual doorway travel; direct F recovery retains vanilla presentation. */
    public static void escape(ServerPlayer player, Endpoint endpoint, Vec3 preferred, Endpoint source) {
        ServerLevel destination = endpoint==null ? null : player.server.getLevel(endpoint.dimension());
        Vec3 landing = destination==null ? null : nearby(player,destination,preferred==null ? endpoint.center() : preferred);
        if (landing==null) {
            destination = player.server.overworld();
            var border = destination.getWorldBorder();
            var spawn = destination.getSharedSpawnPos();
            int x = (int)Math.max(border.getMinX()+2,Math.min(border.getMaxX()-3,spawn.getX()));
            int z = (int)Math.max(border.getMinZ()+2,Math.min(border.getMaxZ()-3,spawn.getZ()));
            var origin = new BlockPos(x,Math.max(destination.getMinBuildHeight()+3,Math.min(destination.getMaxBuildHeight()-3,spawn.getY())),z);
            landing = nearby(player,destination,Vec3.atBottomCenterOf(origin));
            if (landing==null) landing = refuge(player,destination,origin);
            if (landing==null) {
                Elsebase.LOGGER.error("No safe return or empty refuge near world spawn for {}",player.getUUID());
                Portals.message(player,"elsebase.message.world_spawn_has_no_safe_space_an_operator_must_clear_a_safe_landing_there");
                return;
            }
            Portals.message(player,"elsebase.message.outside_destination_unavailable_returned_to_a_safe_place_near_world_spawn");
        }
        player.stopRiding();
        float yaw=player.getYRot();
        if(source!=null) {
            var actual=new Endpoint(destination.dimension(),BlockPos.containing(landing),endpoint==null?source.facing():endpoint.facing());
            if(endpoint!=null) yaw+=dev.elsebase.preview.PortalView.rotation(source,endpoint);
            dev.elsebase.preview.PreviewServer.transfer(player,source,actual,landing,yaw,player.getXRot());
        }
        player.teleportTo(destination,landing.x,landing.y,landing.z,yaw,player.getXRot());
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance=0;
    }
    /** Last resort for void/blocked spawn worlds: add footing in empty sky; never clear existing builds. */
    private static Vec3 refuge(ServerPlayer player, ServerLevel level, BlockPos spawn) {
        for (int dx=-8;dx<=8;dx++) for (int dz=-8;dz<=8;dz++) {
            int x = spawn.getX()+dx, z = spawn.getZ()+dz;
            if (!level.getWorldBorder().isWithinBounds(new BlockPos(x,spawn.getY(),z))) continue;
            level.getChunkAt(new BlockPos(x,spawn.getY(),z));
            int y = Math.max(level.getMinBuildHeight()+4,Math.min(level.getMaxBuildHeight()-3,
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+4));
            BlockPos feet = new BlockPos(x,y,z);
            var floor = feet.below();
            if (!level.getWorldBorder().isWithinBounds(floor.offset(-1,0,-1)) || !level.getWorldBorder().isWithinBounds(floor.offset(1,0,1))) continue;
            level.getChunkAt(feet);
            boolean occupied=false;
            for (var pos : BlockPos.betweenClosed(floor.offset(-1,0,-1),feet.offset(1,1,1)))
                if (!level.getBlockState(pos).isAir() || level.getBlockEntity(pos)!=null) { occupied=true; break; }
            if (occupied || !level.getEntities(null,new net.minecraft.world.phys.AABB(feet).inflate(1)).isEmpty()) continue;
            for (var pos : BlockPos.betweenClosed(floor.offset(-1,0,-1),floor.offset(1,0,1)))
                level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            Elsebase.LOGGER.warn("Created emergency return refuge at {} for {} because no safe spawn landing existed",feet,player.getUUID());
            return Vec3.atBottomCenterOf(feet);
        }
        return null;
    }
}
