package dev.elsebase.portal;

import dev.elsebase.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Server-authoritative lifecycle, placement and traversal for personal and permanent portal pairs. */
public final class Portals {
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> REQUESTS = new HashMap<>();
    private static final LinkedHashMap<UUID, ServerPlayer> PENDING = new LinkedHashMap<>();
    private static final Map<UUID, Endpoint> LINKING = new HashMap<>();
    private static int arrivalTick = -1, arrivals;
    private Portals() {}

    public static void clear() { COOLDOWNS.clear(); REQUESTS.clear(); PENDING.clear(); LINKING.clear(); arrivalTick = -1; arrivals = 0; }
    public static void logout(UUID id) { PENDING.remove(id); REQUESTS.remove(id); COOLDOWNS.remove(id); LINKING.remove(id); }
    public static void message(ServerPlayer player, String text) { player.displayClientMessage(Component.literal(text), true); }

    public static void request(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        if (now - REQUESTS.getOrDefault(player.getUUID(), now - 10) < 10) return;
        REQUESTS.put(player.getUUID(), now);
        if (PENDING.size() >= 100 && !PENDING.containsKey(player.getUUID())) { message(player, "Elsebase: server busy; try again."); return; }
        PENDING.put(player.getUUID(), player);
    }
    /** One potentially generating summon per tick; requests from the same player coalesce. */
    public static void tick() {
        var iterator = PENDING.values().iterator();
        if (!iterator.hasNext()) return;
        ServerPlayer player = iterator.next(); iterator.remove();
        if (player.hasDisconnected() || !player.isAlive()) return;
        try { summon(player); }
        catch (IllegalArgumentException | IllegalStateException e) {
            Elsebase.LOGGER.warn("Elsebase summon refused for {}: {}", player.getUUID(), e.getMessage());
            message(player, "Elsebase: " + e.getMessage());
        }
    }
    /** Executes a validated summon on the server thread; external requests must use the rate-limited queue. */
    public static void summon(ServerPlayer player) {
        var state = WorldState.get(player.server);
        PortalPair old = state.instant(player.getUUID());
        boolean inside = player.level().dimension().equals(Elsebase.DIMENSION);
        if (inside && (old == null || player.isSpectator() || !player.mayBuild())) { escape(player,old); return; }
        if (player.isSpectator() || !player.mayBuild()) return;
        if (!inside && (!Settings.INSTANT.get() || excluded(player.serverLevel()))) {
            message(player, "New instant entrances are disabled here."); return;
        }
        Endpoint near = nearby(player, old);
        if (near == null) {
            if (inside) escape(player,old);
            else message(player, "No clear 1x2 doorway nearby. Leave room on both sides and a solid floor.");
            return;
        }
        if (!inside && !Anchors.ensure(player)) { message(player, "Reference floor or marker is obstructed or protected."); return; }
        Endpoint inner = inside ? near : state.home(player.server, player.getUUID()).reference();
        Endpoint external = inside ? old.external() : near;
        PortalPair next = new PortalPair(old == null ? UUID.randomUUID() : old.id(), player.getUUID(), false, inner, external);
        if (!replaceInstant(player,old,next,inside)) {
            if (inside) escape(player,old);
            else message(player,"New entrance is protected or blocked. Clear a doorway nearby and try again.");
        }
    }
    private static void escape(ServerPlayer player, PortalPair pair) {
        var endpoint = pair==null ? WorldState.get(player.server).returns.get(player.getUUID()) : pair.external();
        COOLDOWNS.put(player.getUUID(),player.serverLevel().getGameTime()+15);
        ReturnTravel.escape(player,endpoint,null);
        InstantExpiry.used(player);
    }
    private static boolean excluded(ServerLevel level) {
        return Settings.EXCLUDED.get().contains(level.dimension().location().toString());
    }
    private static Endpoint nearby(ServerPlayer player, PortalPair old) {
        Direction facing = player.getDirection().getOpposite();
        for (int distance : new int[]{2, 3, 4}) for (int side : new int[]{0, -2, 2}) {
            BlockPos pos = player.blockPosition().relative(player.getDirection(), distance).relative(facing.getClockWise(), side);
            Endpoint e = new Endpoint(player.level().dimension(), pos, facing);
            var hit = player.level().clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), e.center().add(0, 1, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player) {
                @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(BlockState state,
                        net.minecraft.world.level.BlockGetter level, BlockPos position) {
                    return (state.canBeReplaced() && state.getFluidState().isEmpty() && level.getBlockEntity(position) == null
                            || state.is(Content.PORTAL.get()) && reclaimable(player.server,player.serverLevel(),position,old))
                            ? net.minecraft.world.phys.shapes.Shapes.empty() : super.getBlockShape(state,level,position);
                }
            });
            if (hit.getType() == HitResult.Type.MISS && canFit(player, e, old)) return e;
        }
        return null;
    }
    /** Replaceable vegetation/snow may be cleared; solid terrain, fluids and block entities are retained. */
    public static boolean canFit(ServerPlayer player, Endpoint endpoint, PortalPair replacing) {
        ServerLevel level = player.server.getLevel(endpoint.dimension());
        if (level == null) return false;
        if (!oneChunk(endpoint)) return false;
        level.getChunkAt(endpoint.position());
        for (BlockPos pos : endpoint.blocks()) {
            if (!level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return false;
            BlockState s = level.getBlockState(pos);
            PortalPair existing = WorldState.get(player.server).at(endpoint.dimension(), pos);
            if (existing != null && (replacing == null || !existing.id().equals(replacing.id()))) return false;
            if (!replaceable(level, pos) && !(s.is(Content.PORTAL.get()) && reclaimable(player.server,level,pos,replacing))) return false;
            if (!level.mayInteract(player, pos) && !(s.is(Content.PORTAL.get()) && reclaimable(player.server,level,pos,replacing))) return false;
        }
        for (int x = 0; x < Endpoint.WIDTH; x++) {
            BlockPos foot = endpoint.position().relative(endpoint.right(), x);
            if (!level.getBlockState(foot.below()).isFaceSturdy(level, foot.below(), Direction.UP)) return false;
            for (int side : new int[]{-1, 1}) for (int y = 0; y < Endpoint.HEIGHT; y++) {
                BlockPos clear = foot.relative(endpoint.facing(), side).above(y);
                if (!replaceable(level, clear) && !level.getBlockState(clear).is(Content.ANCHOR.get())
                        && !(level.getBlockState(clear).is(Content.PORTAL.get()) && reclaimable(player.server,level,clear,replacing))) return false;
            }
        }
        return true;
    }
    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.canBeReplaced() && state.getFluidState().isEmpty() && level.getBlockEntity(pos) == null;
    }
    private static boolean oneChunk(Endpoint e) {
        int x = e.position().getX() >> 4, z = e.position().getZ() >> 4;
        for (BlockPos p : e.blocks()) for (int side : new int[]{-1, 1}) {
            BlockPos q = p.relative(e.facing(), side);
            if (q.getX() >> 4 != x || q.getZ() >> 4 != z) return false;
        }
        return true;
    }
    /** Only unregistered remnants and the pair being replaced can be reclaimed; other owners remain protected. */
    private static boolean reclaimable(MinecraftServer server, ServerLevel level, BlockPos pos, PortalPair old) {
        PortalPair registered = WorldState.get(server).at(level.dimension(),pos);
        return registered==null || old!=null && registered.id().equals(old.id());
    }
    /** An instant recall never rebuilds or validates its unchanged external return endpoint. */
    private static boolean replaceInstant(ServerPlayer player, PortalPair old, PortalPair next, boolean inside) {
        var data = WorldState.get(player.server);
        data.validatePut(next);
        Endpoint required = inside ? next.inner() : next.external();
        if (!placeInstantEndpoint(player,required,old)) return false;
        // The inner frame is convenient for leaving, but entry resolves the independent spawn anchor.
        if (!inside) placeInstantEndpoint(player,next.inner(),old);
        data.put(next);
        data.rememberReturn(player.getUUID(),next.external());
        InstantExpiry.used(player);
        if (old!=null) for (var endpoint : List.of(old.inner(),old.external())) {
            var level = player.server.getLevel(endpoint.dimension());
            if (level==null) continue;
            level.getChunkAt(endpoint.position());
            for (var pos : endpoint.blocks()) {
                if (data.at(endpoint.dimension(),pos)==null && level.getBlockState(pos).is(Content.PORTAL.get()))
                    level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            }
        }
        return true;
    }
    /** New terrain edits honor claims; replacing owned/orphan portal state itself cannot strand its owner. */
    private static boolean placeInstantEndpoint(ServerPlayer player, Endpoint endpoint, PortalPair old) {
        if (!canFit(player,endpoint,old)) return false;
        var level = player.server.getLevel(endpoint.dimension());
        Map<BlockPos,BlockState> desired = new LinkedHashMap<>();
        for (var pos : endpoint.blocks()) for (int side : new int[]{-1,1}) {
            var approach = pos.relative(endpoint.facing(),side);
            if (replaceable(level,approach) || level.getBlockState(approach).is(Content.PORTAL.get()) && reclaimable(player.server,level,approach,old))
                desired.put(approach,Blocks.AIR.defaultBlockState());
        }
        for (var pos : endpoint.blocks()) desired.put(pos,PortalBlock.stateAt(endpoint,pos));
        List<WorldEdits.Change> changes = new ArrayList<>();
        desired.forEach((pos,after) -> {
            var before = level.getBlockState(pos);
            if (!before.equals(after) && !before.is(Content.PORTAL.get())) changes.add(new WorldEdits.Change(level,pos,before,after));
        });
        if (!WorldEdits.apply(player,changes)) return false;
        desired.forEach((pos,after) -> {
            if (level.getBlockState(pos).is(Content.PORTAL.get()) && reclaimable(player.server,level,pos,old)) level.setBlockAndUpdate(pos,after);
        });
        return true;
    }
    private record Position(ServerLevel level, BlockPos pos) {}
    private static boolean replace(ServerPlayer player, PortalPair old, PortalPair next) {
        if (next != null && (!canFit(player, next.inner(), old) || !canFit(player, next.external(), old))) return false;
        Map<Position, BlockState> desired = new LinkedHashMap<>();
        if (old != null) for (Endpoint endpoint : List.of(old.inner(), old.external())) {
            ServerLevel level = player.server.getLevel(endpoint.dimension());
            if (level == null) continue;
            level.getChunkAt(endpoint.position());
            for (BlockPos pos : endpoint.blocks()) if (level.getBlockState(pos).is(Content.PORTAL.get()))
                desired.put(new Position(level, pos), Blocks.AIR.defaultBlockState());
        }
        if (next != null) for (Endpoint endpoint : List.of(next.inner(), next.external())) {
            ServerLevel level = player.server.getLevel(endpoint.dimension());
            // Approach snow can have collision; clear it in the same claim-aware transaction as the frame.
            for (BlockPos pos : endpoint.blocks()) for (int side : new int[]{-1,1}) {
                BlockPos approach = pos.relative(endpoint.facing(),side);
                if (!level.getBlockState(approach).isAir() && replaceable(level,approach))
                    desired.put(new Position(level,approach),Blocks.AIR.defaultBlockState());
            }
            for (BlockPos pos : endpoint.blocks())
                desired.put(new Position(level, pos), PortalBlock.stateAt(endpoint, pos));
        }
        List<WorldEdits.Change> changes = new ArrayList<>();
        desired.forEach((position, block) -> {
            BlockState before = position.level().getBlockState(position.pos());
            if (!before.equals(block)) changes.add(new WorldEdits.Change(position.level(), position.pos(), before, block));
        });
        if (!WorldEdits.apply(player, changes)) return false;
        var data = WorldState.get(player.server);
        if (next != null) data.put(next); else if (old != null) data.remove(old.id());
        return true;
    }

    public static void anchor(ServerPlayer player, BlockPos marker) {
        if (!player.level().dimension().equals(Elsebase.DIMENSION)) { message(player, "Anchors belong in the Backdoor."); return; }
        var level = player.serverLevel();
        if (!level.getBlockState(marker).isAir() || !level.getBlockState(marker.below()).isFaceSturdy(level, marker.below(), Direction.UP)) {
            message(player, "Place the anchor on a clear solid floor."); return;
        }
        // The marker sits at the arrival side of the reference doorway.
        Direction facing = player.getDirection().getOpposite();
        Endpoint ref = new Endpoint(Elsebase.DIMENSION, marker.relative(facing.getOpposite()), facing);
        var data = WorldState.get(player.server);
        if (!canFit(player, ref, data.instant(player.getUUID()))) { message(player, "The anchor needs a clear doorway beside it."); return; }
        var home = data.home(player.server, player.getUUID());
        List<WorldEdits.Change> changes = new ArrayList<>();
        if (home.anchor() != null && !home.anchor().equals(marker)) {
            level.getChunkAt(home.anchor());
            if (level.getBlockState(home.anchor()).is(Content.ANCHOR.get()))
                changes.add(new WorldEdits.Change(level, home.anchor(), level.getBlockState(home.anchor()), Blocks.AIR.defaultBlockState()));
        }
        changes.add(new WorldEdits.Change(level, marker, level.getBlockState(marker), Content.ANCHOR.get().defaultBlockState()));
        if (!WorldEdits.apply(player, changes)) { message(player, "Anchor placement denied; previous anchor retained."); return; }
        data.homes.put(player.getUUID(), new WorldState.Home(home.slot(), ref, marker)); data.setDirty();
        Anchors.track(player);
        message(player, "Reference updated. The existing return portal stays where it is.");
    }

    public static void tool(ServerPlayer player, BlockPos clicked, BlockPos place) {
        var data = WorldState.get(player.server);
        PortalPair pair = data.at(player.level().dimension(), clicked);
        if (pair != null) {
            if (!player.isShiftKeyDown()) { message(player, "Sneak-use the tool to remove this pair."); return; }
            if (!pair.owner().equals(player.getUUID()) && !player.hasPermissions(2)) { message(player, "Only the owner or an operator can remove this pair."); return; }
            if (replace(player, pair, null) && pair.permanent()) player.getInventory().placeItemBackInInventory(new ItemStack(Content.CORE.get()));
            return;
        }
        Endpoint endpoint = new Endpoint(player.level().dimension(), place, player.getDirection().getOpposite());
        if (!canFit(player, endpoint, null)) { message(player, "A clear 1x2 doorway and floor are required, away from chunk edges."); return; }
        if (endpoint.inner()) {
            LINKING.put(player.getUUID(), endpoint);
            message(player, "Inner endpoint selected. Use this tool outside to spend one Threshold Core and complete the pair."); return;
        }
        Endpoint inner = LINKING.get(player.getUUID());
        if (inner == null) { message(player, "Select the inner endpoint in the Backdoor first."); return; }
        if (excluded(player.serverLevel())) { message(player, "New entrances are disabled in this dimension."); return; }
        long count = data.pairs.values().stream().filter(p -> p.permanent() && p.owner().equals(player.getUUID())).count();
        if (count >= Settings.PERMANENT_LIMIT.get()) { message(player, "Permanent portal limit reached."); return; }
        int coreSlot = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i).is(Content.CORE.get())) { coreSlot = i; break; }
        if (coreSlot < 0 && !player.isCreative()) { message(player, "One Threshold Core is required."); return; }
        if (replace(player, null, new PortalPair(UUID.randomUUID(), player.getUUID(), true, inner, endpoint))) {
            if (!player.isCreative()) player.getInventory().getItem(coreSlot).shrink(1);
            LINKING.remove(player.getUUID()); message(player, "Permanent portal linked.");
        } else message(player, "Endpoint obstructed or protected. No core consumed.");
    }

    /** Contact with the doorway interior triggers travel even while falling through unsupported frames. */
    public static void cross(ServerPlayer player, BlockPos surface) {
        if (player.isPassenger() || player.isVehicle() || !player.isAlive()) return;
        long now = player.serverLevel().getGameTime();
        if (now < COOLDOWNS.getOrDefault(player.getUUID(), 0L)) return;
        var data = WorldState.get(player.server);
        PortalPair pair = data.at(player.level().dimension(), surface);
        if (pair == null || !pair.permanent() && !pair.owner().equals(player.getUUID())) return;
        Endpoint source = player.level().dimension().equals(Elsebase.DIMENSION) ? pair.inner() : pair.external();
        Endpoint target = source.inner() ? pair.external() : pair.inner();
        Vec3 center = source.center();
        Vec3 normal = Vec3.atLowerCornerOf(source.facing().getNormal());
        double before = new Vec3(player.xo, player.yo, player.zo).subtract(center).dot(normal);
        if (!PortalBlock.trigger(source).intersects(player.getBoundingBox())) return;
        double lateral = player.position().subtract(center).dot(Vec3.atLowerCornerOf(source.right().getNormal()));
        lateral = Math.max(-0.19,Math.min(0.19,lateral));
        int tick = player.server.getTickCount();
        if (arrivalTick != tick) { arrivalTick = tick; arrivals = 0; }
        // Bound synchronous destination generation even when proactive mirror loading is disabled.
        if (arrivals >= 2) { message(player, "Portal traffic busy; step back and try again."); return; }
        arrivals++;
        ServerLevel destination = player.server.getLevel(target.dimension());
        if (destination == null && !source.inner()) { blocked(player, now); return; }
        Vec3 exit = target.center().add(Vec3.atLowerCornerOf(target.right().getNormal()).scale(-lateral))
                .add(Vec3.atLowerCornerOf(target.facing().getNormal()).scale(before >= 0 ? 0.9 : -0.9))
                .add(0, player.getY() - center.y, 0);
        if (source.inner()) {
            COOLDOWNS.put(player.getUUID(),now+15);
            ReturnTravel.escape(player,target,exit);
            InstantExpiry.used(player);
            return;
        }
        if (!complete(player.server,source)) { blocked(player,now); return; }
        if (!pair.permanent()) {
            if (!Anchors.ensure(player)) { blocked(player,now); return; }
            exit = Vec3.atBottomCenterOf(data.home(player.server,player.getUUID()).anchor());
        } else {
            destination.getChunkAt(target.position());
            if (!complete(player.server,target)) { blocked(player,now); return; }
        }
        destination.getChunkAt(BlockPos.containing(exit));
        if (!ReturnTravel.safe(player,destination,exit)) { blocked(player,now); return; }
        data.rememberReturn(player.getUUID(),source);
        float rotation = target.facing().toYRot() - source.facing().toYRot() + 180;
        Vec3 velocity = pair.permanent() ? player.getDeltaMovement().yRot((float) -Math.toRadians(rotation)) : Vec3.ZERO;
        COOLDOWNS.put(player.getUUID(), now + 15);
        player.teleportTo(destination, exit.x, exit.y, exit.z, Set.of(), player.getYRot() + rotation, player.getXRot());
        player.setDeltaMovement(velocity); player.fallDistance = 0;
        InstantExpiry.used(player);
    }
    private static boolean complete(MinecraftServer server, Endpoint e) {
        ServerLevel level = server.getLevel(e.dimension());
        if (level == null) return false;
        for (BlockPos p : e.blocks()) {
            BlockState s = level.getBlockState(p);
            if (!s.is(Content.PORTAL.get()) || s.getValue(PortalBlock.FACING) != e.facing()) return false;
        }
        return true;
    }
    private static void blocked(ServerPlayer player, long now) {
        COOLDOWNS.put(player.getUUID(), now + 20);
        message(player, "Portal unavailable: destination missing, obstructed or unsafe.");
    }
}
