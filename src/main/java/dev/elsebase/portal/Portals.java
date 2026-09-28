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
    private static final Map<UUID, Set<Endpoint>> ARRIVAL_CONTACTS = new HashMap<>();
    private static final List<BlockPos> SUMMON_OFFSETS = summonOffsets();
    private static int arrivalTick = -1, arrivals;
    private Portals() {}

    public static void clear() { ARRIVAL_CONTACTS.clear(); COOLDOWNS.clear(); REQUESTS.clear(); PENDING.clear(); LINKING.clear(); arrivalTick = -1; arrivals = 0; }
    public static void logout(UUID id) { ARRIVAL_CONTACTS.remove(id); PENDING.remove(id); REQUESTS.remove(id); COOLDOWNS.remove(id); LINKING.remove(id); }
    public static void message(ServerPlayer player, String text) { player.displayClientMessage(text.startsWith("elsebase.message.") ? Component.translatable(text) : Component.literal(text), true); }

    public static void request(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        if (now - REQUESTS.getOrDefault(player.getUUID(), now - 10) < 10) return;
        REQUESTS.put(player.getUUID(), now);
        if (PENDING.size() >= 100 && !PENDING.containsKey(player.getUUID())) { message(player, "elsebase.message.elsebase_server_busy_try_again"); return; }
        PENDING.put(player.getUUID(), player);
    }
    /** One potentially generating summon per tick; requests from the same player coalesce. */
    public static void tick(MinecraftServer server) {
        for (var id : List.copyOf(ARRIVAL_CONTACTS.keySet())) {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) ARRIVAL_CONTACTS.remove(id);
            else updateArrivalContact(player);
        }
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
        // Check on execution, not only enqueue: a player may be attacked while waiting in the queue.
        if (CombatLock.blocked(player)) { message(player, "elsebase.message.combat_lock"); return; }
        var state = WorldState.get(player.server);
        PortalPair old = state.instant(player.getUUID());
        boolean inside = player.level().dimension().equals(Elsebase.DIMENSION);
        if (inside && Settings.REQUIRE_KNOWN_RETURN.get() && old == null && !state.returns.containsKey(player.getUUID())) {
            message(player, "elsebase.message.no_known_return"); return;
        }
        if (inside && (old == null || player.isSpectator() || !player.mayBuild())) { escape(player,old); return; }
        if (player.isSpectator() || !player.mayBuild()) return;
        if (!inside && (!Settings.INSTANT.get() || excluded(player.serverLevel()))) {
            message(player, "elsebase.message.new_instant_entrances_are_disabled_here"); return;
        }
        Endpoint near = nearby(player, old);
        if (near == null) {
            if (inside) escape(player,old);
            else message(player, "elsebase.message.no_free_1x2_doorway_nearby");
            return;
        }
        if (!inside) Anchors.ensure(player); // Prepare normally; actual entry resolves obstruction recovery.
        Endpoint inner = inside ? near : state.home(player.server, player.getUUID()).reference();
        Endpoint external = inside ? old.external() : near;
        PortalPair next = new PortalPair(old == null ? UUID.randomUUID() : old.id(), player.getUUID(), false, inner, external);
        if (!replaceInstant(player,old,next,inside)) {
            if (inside) escape(player,old);
            else message(player,"elsebase.message.new_entrance_is_protected_or_blocked_clear_a_doorway_nearby_and_try_again");
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
    /** Bounded forward half-disc, including immediate/side cells and nearby terrain elevations. */
    private static List<BlockPos> summonOffsets() {
        var offsets = new ArrayList<BlockPos>();
        for (int forward=0;forward<=4;forward++) for (int side=-4;side<=4;side++) {
            if (forward*forward+side*side>16 || forward==0 && side==0) continue;
            for (int dy=-2;dy<=2;dy++) offsets.add(new BlockPos(side,dy,forward));
        }
        // Preserve the familiar two-block-ahead position; fill all gaps before considering distant cells.
        offsets.sort(Comparator.comparingInt(p -> p.getX()*p.getX() + (p.getZ()-2)*(p.getZ()-2) + 2*p.getY()*p.getY()));
        return List.copyOf(offsets);
    }
    private static Endpoint nearby(ServerPlayer player, PortalPair old) {
        Direction facing = player.getDirection().getOpposite();
        var level = player.serverLevel();
        for (var offset : SUMMON_OFFSETS) {
            BlockPos pos = player.blockPosition().relative(player.getDirection(),offset.getZ())
                    .relative(facing.getClockWise(),offset.getX()).above(offset.getY());
            if (!level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)
                    || level.isOutsideBuildHeight(pos.below()) || level.isOutsideBuildHeight(pos.above())) continue;
            var support = level.getBlockState(pos.below());
            if (!support.isFaceSturdy(level,pos.below(),Direction.UP) || ReturnTravel.hazardous(support)) continue;
            Endpoint e = new Endpoint(level.dimension(), pos, facing);
            if (PortalBlock.trigger(e).intersects(player.getBoundingBox()) || !canFit(player,e,old)) continue;
            var hit = player.level().clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), e.center().add(0, 1, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player) {
                @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(BlockState state,
                        net.minecraft.world.level.BlockGetter level, BlockPos position) {
                    return (state.canBeReplaced() && state.getFluidState().isEmpty() && level.getBlockEntity(position) == null
                            || state.is(Content.PORTAL.get()) && reclaimable(player.server,player.serverLevel(),position,old))
                            ? net.minecraft.world.phys.shapes.Shapes.empty() : super.getBlockShape(state,level,position);
                }
            });
            if (hit.getType() == HitResult.Type.MISS) return e;
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
        return true;
    }
    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.canBeReplaced() && state.getFluidState().isEmpty() && level.getBlockEntity(pos) == null;
    }
    private static boolean oneChunk(Endpoint e) {
        int x=e.position().getX()>>4,z=e.position().getZ()>>4;
        for(BlockPos p:e.blocks()) if(p.getX()>>4!=x || p.getZ()>>4!=z) return false;
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
        if (!player.level().dimension().equals(Elsebase.DIMENSION)) { message(player, "elsebase.message.anchors_belong_in_the_backdoor"); return; }
        var level = player.serverLevel();
        if (!level.getBlockState(marker).isAir() || !level.getBlockState(marker.below()).isFaceSturdy(level, marker.below(), Direction.UP)) {
            message(player, "elsebase.message.place_the_anchor_on_a_clear_solid_floor"); return;
        }
        // The marker sits at the arrival side of the reference doorway.
        Direction facing = player.getDirection().getOpposite();
        Endpoint ref = new Endpoint(Elsebase.DIMENSION, marker.relative(facing.getOpposite()), facing);
        var data = WorldState.get(player.server);
        if (!canFit(player, ref, data.instant(player.getUUID()))) { message(player, "elsebase.message.the_anchor_needs_a_clear_doorway_beside_it"); return; }
        var home = data.home(player.server, player.getUUID());
        List<WorldEdits.Change> changes = new ArrayList<>();
        if (home.anchor() != null && !home.anchor().equals(marker)) {
            level.getChunkAt(home.anchor());
            if (level.getBlockState(home.anchor()).is(Content.ANCHOR.get()))
                changes.add(new WorldEdits.Change(level, home.anchor(), level.getBlockState(home.anchor()), Blocks.AIR.defaultBlockState()));
        }
        changes.add(new WorldEdits.Change(level, marker, level.getBlockState(marker), Content.ANCHOR.get().defaultBlockState()));
        if (!WorldEdits.apply(player, changes)) { message(player, "elsebase.message.anchor_placement_denied_previous_anchor_retained"); return; }
        data.homes.put(player.getUUID(), new WorldState.Home(home.slot(), ref, marker)); data.setDirty();
        Anchors.track(player);
        message(player, "elsebase.message.reference_updated_the_existing_return_portal_stays_where_it_is");
    }

    public static void tool(ServerPlayer player, BlockPos clicked, BlockPos place) {
        var data = WorldState.get(player.server);
        PortalPair pair = data.at(player.level().dimension(), clicked);
        if (pair != null) {
            if (!player.isShiftKeyDown()) { message(player, "elsebase.message.sneak_use_the_tool_to_remove_this_pair"); return; }
            if (!pair.owner().equals(player.getUUID()) && !player.hasPermissions(2)) { message(player, "elsebase.message.only_the_owner_or_an_operator_can_remove_this_pair"); return; }
            if (replace(player, pair, null) && pair.permanent()) player.getInventory().placeItemBackInInventory(new ItemStack(Content.CORE.get()));
            return;
        }
        Endpoint endpoint = new Endpoint(player.level().dimension(), place, player.getDirection().getOpposite());
        if (!canFit(player, endpoint, null)) { message(player, "elsebase.message.a_free_1x2_doorway_is_required"); return; }
        if (endpoint.inner()) {
            LINKING.put(player.getUUID(), endpoint);
            message(player, "elsebase.message.inner_endpoint_selected_use_this_tool_outside_to_spend_one_threshold_core_and_complete_the_pair"); return;
        }
        Endpoint inner = LINKING.get(player.getUUID());
        if (inner == null) { message(player, "elsebase.message.select_the_inner_endpoint_in_the_backdoor_first"); return; }
        if (excluded(player.serverLevel())) { message(player, "elsebase.message.new_entrances_are_disabled_in_this_dimension"); return; }
        long count = data.pairs.values().stream().filter(p -> p.permanent() && p.owner().equals(player.getUUID())).count();
        if (count >= Settings.PERMANENT_LIMIT.get()) { message(player, "elsebase.message.permanent_portal_limit_reached"); return; }
        int coreSlot = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i).is(Content.CORE.get())) { coreSlot = i; break; }
        if (coreSlot < 0 && !player.isCreative()) { message(player, "elsebase.message.one_threshold_core_is_required"); return; }
        if (replace(player, null, new PortalPair(UUID.randomUUID(), player.getUUID(), true, inner, endpoint))) {
            if (!player.isCreative()) player.getInventory().getItem(coreSlot).shrink(1);
            LINKING.remove(player.getUUID()); message(player, "elsebase.message.permanent_portal_linked");
        } else message(player, "elsebase.message.endpoint_obstructed_or_protected_no_core_consumed");
    }

    /** Suppress destination surfaces until the arriving body has actually left them, regardless of elapsed time. */
    public static void arrived(ServerPlayer player) {
        var contacts = new HashSet<Endpoint>();
        var box = player.getBoundingBox();
        var data = WorldState.get(player.server);
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))) {
            var pair = data.at(player.level().dimension(),pos);
            if (pair == null) continue;
            var endpoint = player.level().dimension().equals(Elsebase.DIMENSION) ? pair.inner() : pair.external();
            if (PortalBlock.trigger(endpoint).intersects(box)) contacts.add(endpoint);
        }
        if (contacts.isEmpty()) ARRIVAL_CONTACTS.remove(player.getUUID());
        else ARRIVAL_CONTACTS.put(player.getUUID(),contacts);
    }
    /** Called each server tick and before contact processing, so stepping away re-arms normal travel. */
    public static void updateArrivalContact(ServerPlayer player) {
        var contacts = ARRIVAL_CONTACTS.get(player.getUUID());
        if (contacts == null) return;
        contacts.removeIf(endpoint -> !endpoint.dimension().equals(player.level().dimension())
                || !PortalBlock.trigger(endpoint).intersects(player.getBoundingBox()));
        if (contacts.isEmpty()) ARRIVAL_CONTACTS.remove(player.getUUID());
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
        updateArrivalContact(player);
        if (ARRIVAL_CONTACTS.getOrDefault(player.getUUID(),Set.of()).contains(source)) return;
        Endpoint target = dev.elsebase.preview.PortalView.target(data,pair,source);
        Vec3 center = source.center();
        Vec3 normal = Vec3.atLowerCornerOf(source.facing().getNormal());
        double before = new Vec3(player.xo, player.yo, player.zo).subtract(center).dot(normal);
        if (!PortalBlock.trigger(source).intersects(player.getBoundingBox())) return;
        double lateral = player.position().subtract(center).dot(Vec3.atLowerCornerOf(source.right().getNormal()));
        lateral = Math.max(-0.19,Math.min(0.19,lateral));
        int tick = player.server.getTickCount();
        if (arrivalTick != tick) { arrivalTick = tick; arrivals = 0; }
        // Bound synchronous destination generation even when proactive mirror loading is disabled.
        if (arrivals >= 2) { message(player, "elsebase.message.portal_traffic_busy_step_back_and_try_again"); return; }
        arrivals++;
        ServerLevel destination = player.server.getLevel(target.dimension());
        if (destination == null && !source.inner()) { blocked(player, now); return; }
        Vec3 exit = target.center().add(Vec3.atLowerCornerOf(target.right().getNormal()).scale(-lateral))
                .add(Vec3.atLowerCornerOf(target.facing().getNormal()).scale(before >= 0 ? 0.9 : -0.9))
                .add(0, player.getY() - center.y, 0);
        if (source.inner()) {
            COOLDOWNS.put(player.getUUID(),now+15);
            ReturnTravel.escape(player,target,exit,source);
            InstantExpiry.used(player);
            return;
        }
        if (!complete(player.server,source)) { blocked(player,now); return; }
        if (!pair.permanent()) {
            exit = AnchorArrival.resolve(player);
            if (exit == null) { blocked(player,now); return; }
        } else {
            destination.getChunkAt(target.position());
            if (!complete(player.server,target)) { blocked(player,now); return; }
        }
        destination.getChunkAt(BlockPos.containing(exit));
        if (!ReturnTravel.safe(player,destination,exit)) { blocked(player,now); return; }
        data.rememberReturn(player.getUUID(),source);
        float rotation = dev.elsebase.preview.PortalView.rotation(source,target);
        Vec3 velocity = pair.permanent() ? player.getDeltaMovement().yRot((float) -Math.toRadians(rotation)) : Vec3.ZERO;
        COOLDOWNS.put(player.getUUID(), now + 15);
        dev.elsebase.preview.PreviewServer.transfer(player,source,target,exit,player.getYRot()+rotation,player.getXRot());
        var companions = LeashedTravel.capture(player);
        player.teleportTo(destination, exit.x, exit.y, exit.z, Set.of(), player.getYRot() + rotation, player.getXRot());
        arrived(player);
        player.setDeltaMovement(velocity); player.fallDistance = 0;
        companions.follow(player);
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
        message(player, "elsebase.message.portal_unavailable_destination_missing_obstructed_or_unsafe");
    }
}
