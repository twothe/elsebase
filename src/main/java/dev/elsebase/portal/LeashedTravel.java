package dev.elsebase.portal;

import dev.elsebase.Elsebase;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One traversal's leash graph. Native dimension changes preserve entity data and recursively carry passengers. */
public final class LeashedTravel {
    private record Group(Entity root, List<Entity> members) {}
    private record Link(Entity entity, Entity holder) {}
    private final ServerLevel source;
    private final List<Group> groups;
    private final List<Link> links;

    private LeashedTravel(ServerLevel source, List<Group> groups, List<Link> links) {
        this.source = source; this.groups = groups; this.links = links;
    }

    /** Inspect loaded entities once per actual crossing; never infer pet ownership or load source chunks. */
    public static LeashedTravel capture(ServerPlayer player) {
        var held = new IdentityHashMap<Entity, List<Entity>>();
        for (var entity : player.serverLevel().getAllEntities()) {
            if (entity.isAlive() && entity instanceof Leashable leash && leash.getLeashHolder() != null)
                held.computeIfAbsent(leash.getLeashHolder(), key -> new ArrayList<>()).add(entity);
        }
        var selected = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
        var pending = new ArrayDeque<Entity>(); pending.add(player);
        var groups = new ArrayList<Group>();
        while (!pending.isEmpty()) for (var entity : held.getOrDefault(pending.remove(), List.of())) {
            var root = entity.getRootVehicle();
            if (root == player || selected.contains(root)) continue;
            var members = root.getSelfAndPassengers().toList();
            groups.add(new Group(root, members));
            for (var member : members) if (selected.add(member)) pending.add(member);
        }
        var links = new ArrayList<Link>();
        for (var entity : selected) if (entity instanceof Leashable leash && leash.getLeashHolder() != null)
            links.add(new Link(entity, leash.getLeashHolder()));
        return new LeashedTravel(player.serverLevel(), groups, links);
    }

    /** Run only after successful player travel. Unsafe/refused companions stay behind; never block the player's escape. */
    public void follow(ServerPlayer player) {
        var destination = player.serverLevel();
        if (source == destination || groups.isEmpty()) return;
        var occupied = new ArrayList<AABB>(); occupied.add(player.getBoundingBox());
        // Newly added destination entities need not be visible in ServerLevel's lookup until its chunk becomes tracked.
        var transferred = new HashMap<UUID, Entity>();
        boolean incomplete = false;
        for (var group : groups) {
            if (group.members.stream().anyMatch(entity -> !entity.isAlive() || entity.level() != source
                    || !entity.canChangeDimensions(source, destination))) { incomplete = true; continue; }
            positionPassengers(group.root);
            AABB bounds = group.root.getBoundingBox();
            for (var member : group.members) bounds = bounds.minmax(member.getBoundingBox());
            var landing = landing(group.root, bounds, destination, player.position(), occupied);
            if (landing == null) { incomplete = true; continue; }
            try {
                var moved = group.root.changeDimension(new DimensionTransition(destination, landing, Vec3.ZERO,
                        group.root.getYRot(), group.root.getXRot(), entity -> {
                            transferred.put(entity.getUUID(), entity);
                            entity.placePortalTicket(entity.blockPosition());
                        }));
                if (moved != null) {
                    positionPassengers(moved);
                    moved.getSelfAndPassengers().forEach(entity -> { transferred.put(entity.getUUID(),entity); entity.fallDistance = 0; entity.setDeltaMovement(Vec3.ZERO); occupied.add(entity.getBoundingBox()); });
                }
                // Native/mod hooks can refuse individual passengers, so inspect actual destination membership.
                for (var member : group.members) if (!transferred.containsKey(member.getUUID())) incomplete = true;
            } catch (RuntimeException error) {
                incomplete = true;
                Elsebase.LOGGER.error("Leashed portal transfer failed for entity {} ({}) from {} to {}", group.root.getUUID(),
                        group.root.getType(), source.dimension().location(), destination.dimension().location(), error);
            }
        }
        for (var link : links) {
            Entity entity = transferred.get(link.entity.getUUID());
            if (entity == null && !link.entity.isRemoved()) entity = link.entity;
            Entity holder = link.holder == player ? player : transferred.get(link.holder.getUUID());
            if (holder == null && !link.holder.isRemoved()) holder = link.holder;
            if (!(entity instanceof Leashable leash)) continue;
            if (holder != null && holder.level() == entity.level()) {
                // setLeashedTo dismounts passengers; ordinary carried boats are roots, but modded riders may also be leashed.
                if (entity.isPassenger() && leash.getLeashData() != null) {
                    leash.getLeashData().setLeashHolder(holder);
                    ((ServerLevel)entity.level()).getChunkSource().broadcast(entity,
                            new net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket(entity, holder));
                } else leash.setLeashedTo(holder, true);
            } else {
                // Resolve a copied delayed leash before dropping so exactly one lead is returned for the broken connection.
                if (leash.getLeashData() != null) leash.getLeashData().setLeashHolder(link.holder);
                leash.dropLeash(true, true);
                incomplete = true;
            }
        }
        if (incomplete) Portals.message(player, "elsebase.message.leashed_transfer_incomplete");
    }

    private static void positionPassengers(Entity root) {
        for (var passenger : root.getPassengers()) { root.positionRider(passenger); positionPassengers(passenger); }
    }

    /** Search only near the arrival, including full passenger headroom; never clear terrain for livestock. */
    private static Vec3 landing(Entity root, AABB bounds, ServerLevel level, Vec3 arrival, List<AABB> occupied) {
        // Reject pathological modded dimensions before bounded block/collision scans.
        if (!Double.isFinite(bounds.getSize()) || bounds.getXsize() > 16 || bounds.getZsize() > 16 || bounds.getYsize() > 24) return null;
        var origin = BlockPos.containing(arrival);
        for (int radius = 0; radius <= 6; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                var feet = origin.offset(dx, dy, dz); var candidate = Vec3.atBottomCenterOf(feet);
                var box = bounds.move(candidate.subtract(root.position()));
                if (!level.getWorldBorder().isWithinBounds(box) || box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                        || occupied.stream().anyMatch(other -> other.intersects(box))) continue;
                for (int x = BlockPos.containing(box.minX, 0, 0).getX() >> 4; x <= BlockPos.containing(box.maxX, 0, 0).getX() >> 4; x++)
                    for (int z = BlockPos.containing(0, 0, box.minZ).getZ() >> 4; z <= BlockPos.containing(0, 0, box.maxZ).getZ() >> 4; z++) level.getChunk(x, z);
                var support = level.getBlockState(feet.below());
                if (!support.isFaceSturdy(level, feet.below(), Direction.UP) || ReturnTravel.hazardous(support)
                        || !level.noCollision(root, box) || level.containsAnyLiquid(box)) continue;
                boolean hazardous = false;
                for (var pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
                    if (ReturnTravel.hazardous(level.getBlockState(pos))) { hazardous = true; break; }
                if (!hazardous) return candidate;
            }
        }
        return null;
    }
}
