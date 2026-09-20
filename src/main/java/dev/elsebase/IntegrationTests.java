package dev.elsebase;

import dev.elsebase.portal.*;
import dev.elsebase.world.RoomLayout;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Development-only GameTests exercise registry initialization, generation and persisted gameplay contracts. */
@PrefixGameTestTemplate(false)
public final class IntegrationTests {
    public static void register(RegisterGameTestsEvent event) { event.register(IntegrationTests.class); }
    @BeforeBatch(batch = "defaultBatch")
    public static void resetTestRegistry(net.minecraft.server.level.ServerLevel level) {
        if (!Boolean.getBoolean("neoforge.gameTestServer")) return;
        var data = WorldState.get(level.getServer());
        for (var pair : new ArrayList<>(data.pairs.values())) {
            for (var endpoint : List.of(pair.inner(), pair.external())) {
                var world = level.getServer().getLevel(endpoint.dimension());
                if (world != null) for (var pos : endpoint.blocks())
                    if (world.getBlockState(pos).is(Content.PORTAL.get())) world.removeBlock(pos,false);
            }
            data.remove(pair.id());
        }
        data.homes.clear(); data.setDirty();
    }

    @GameTest(template = "empty", templateNamespace = Elsebase.ID, timeoutTicks = 400)
    public static void mirrorTicketsDoNotCascade(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var inner = server.getLevel(Elsebase.DIMENSION);
        var outer = server.overworld(); var nether = server.getLevel(Level.NETHER);
        var a = new Endpoint(Elsebase.DIMENSION,new BlockPos(320008,65,320008),Direction.SOUTH);
        var b = new Endpoint(Level.OVERWORLD,new BlockPos(160008,65,160008),Direction.SOUTH);
        var c = new Endpoint(Elsebase.DIMENSION,new BlockPos(320010,65,320010),Direction.NORTH);
        var d = new Endpoint(Level.NETHER,new BlockPos(480008,65,480008),Direction.SOUTH);
        var owner = UUID.randomUUID();
        var first = new PortalPair(UUID.randomUUID(),owner,true,a,b);
        var second = new PortalPair(UUID.randomUUID(),owner,true,c,d);
        var data = WorldState.get(server); data.put(first); data.put(second);
        var outerChunk = new net.minecraft.world.level.ChunkPos(b.position());
        var innerChunk = new net.minecraft.world.level.ChunkPos(a.position());
        var farChunk = new net.minecraft.world.level.ChunkPos(d.position());
        try {
            outer.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,outerChunk,2,outerChunk);
            MirrorLoading.tick(server);
            helper.assertTrue(hasMirror(inner,innerChunk),"Independent source loads direct counterpart");
            MirrorLoading.tick(server);
            helper.assertTrue(!hasMirror(nether,farChunk),"Mirror-only source cannot cascade");
            inner.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,innerChunk,2,innerChunk);
            MirrorLoading.tick(server);
            helper.assertTrue(hasMirror(nether,farChunk),"Independent ticket coexisting with mirror is recognized");
            outer.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,outerChunk,2,outerChunk);
            inner.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,innerChunk,2,innerChunk);
            MirrorLoading.tick(server);
            helper.assertTrue(!hasMirror(nether,farChunk),"Counterpart ticket released after independent root disappears");
        } finally {
            outer.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,outerChunk,2,outerChunk);
            inner.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,innerChunk,2,innerChunk);
            data.remove(first.id()); data.remove(second.id()); MirrorLoading.clear();
        }
        helper.succeed();
    }
    private static boolean hasMirror(net.minecraft.server.level.ServerLevel level,net.minecraft.world.level.ChunkPos chunk) {
        var tickets = level.getChunkSource().chunkMap.getDistanceManager().tickets.get(chunk.toLong());
        return tickets != null && tickets.stream().anyMatch(t -> t.getType() == MirrorLoading.MIRROR);
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty", templateNamespace = Elsebase.ID, timeoutTicks = 400)
    public static void permanentPortalsAndPanels(GameTestHelper helper) {
        // Vanilla's helper overrides isCreative() to true even after setGameMode; use a real survival-capable player.
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "elsebase-survival");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),profile,cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.server.getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var inner = player.server.getLevel(Elsebase.DIMENSION);
        var outside = helper.getLevel();
        var data = WorldState.get(player.server);
        player.getInventory().add(new net.minecraft.world.item.ItemStack(Content.CORE.get(),2));
        for (int x = 35; x <= 45; x++) for (int z = 3; z <= 13; z++) {
            outside.setBlockAndUpdate(new BlockPos(x,149,z), Blocks.STONE.defaultBlockState());
            for (int y=150;y<155;y++) outside.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        player.teleportTo(inner,104.5,65,104.5,0,0);
        var firstInner = new BlockPos(104,65,106);
        Portals.tool(player,firstInner.below(),firstInner);
        player.teleportTo(outside,40.5,150,6.5,0,0);
        var firstOutside = new BlockPos(40,150,8);
        Portals.tool(player,firstOutside.below(),firstOutside);
        var pair = data.at(Level.OVERWORLD,firstOutside);
        helper.assertTrue(pair != null && pair.permanent(),"Permanent pair linked");
        helper.assertTrue(player.getInventory().countItem(Content.CORE.get())==1,"Exactly one core consumed in survival");
        int oldLimit = Settings.PERMANENT_LIMIT.get();
        try {
            Settings.PERMANENT_LIMIT.set(1);
            player.teleportTo(inner,108.5,65,104.5,0,0);
            var next = new BlockPos(108,65,108); Portals.tool(player,next.below(),next);
            player.teleportTo(outside,40.5,150,9.5,0,0);
            next = new BlockPos(40,150,11); Portals.tool(player,next.below(),next);
            helper.assertTrue(data.at(Level.OVERWORLD,next)==null,"Owner pair limit enforced");
            helper.assertTrue(player.getInventory().countItem(Content.CORE.get())==1,"Rejected link spends no core");
        } finally { Settings.PERMANENT_LIMIT.set(oldLimit); }
        player.setShiftKeyDown(true);
        Portals.tool(player,firstOutside,firstOutside);
        helper.assertTrue(data.at(Level.OVERWORLD,firstOutside)==null && inner.getBlockState(firstInner).isAir(),"Tool removes both endpoints");
        helper.assertTrue(player.getInventory().countItem(Content.CORE.get())==2,"Removing pair refunds one core");
        player.setShiftKeyDown(false);
        player.teleportTo(inner,104.5,65,104.5,0,0);
        var floor = new BlockPos(104,64,104); var ceiling = new BlockPos(104,72,104);
        player.setXRot(-90);
        dev.elsebase.structure.StructuralEditor.request(player,false);
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        helper.assertTrue(inner.getBlockState(ceiling).isAir(),"Whole ceiling removed using gaze");
        inner.setBlockAndUpdate(ceiling,Blocks.CHEST.defaultBlockState());
        player.setXRot(-90);
        dev.elsebase.structure.StructuralEditor.request(player,true);
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        helper.assertTrue(inner.getBlockState(ceiling).is(Blocks.CHEST) && inner.getBlockState(ceiling.east()).isAir(),"Occupied restoration is atomic and preserves machine");
        inner.removeBlock(ceiling,false);
        player.setXRot(-90);
        dev.elsebase.structure.StructuralEditor.request(player,true);
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        helper.assertTrue(StructuralBlock.protectedStructure(inner,inner.getBlockState(ceiling)),"Restored ceiling regains structural protection");
        var fake = net.neoforged.neoforge.common.util.FakePlayerFactory.get(inner,new com.mojang.authlib.GameProfile(new UUID(1,2),"elsebase-test-drill"));
        var drill = new BlockPos(110,67,110);
        inner.setBlockAndUpdate(drill,Content.structure(RoomLayout.Material.WALL));
        helper.assertTrue(fake.gameMode.destroyBlock(drill) && inner.getBlockState(drill).isAir(),"Fake-player mining follows ordinary break rules");
        // Retire this fixture's player before server shutdown can start draining its generation queue.
        player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed"));
        helper.runAfterDelay(40,helper::succeed);
    }

    @GameTest(template = "empty", templateNamespace = Elsebase.ID)
    public static void darknessAndSpawning(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Elsebase.DIMENSION);
        var generator = (dev.elsebase.world.RoomGenerator) level.getChunkSource().getGenerator();
        boolean dark = Settings.DARKNESS.get(), mobs = Settings.NATURAL_SPAWNS.get();
        try {
            Settings.DARKNESS.set(false);
            helper.assertTrue(generator.original(2,64,2).is(Content.FLOOR.get()),"Bright generation has ordinary floors");
            var color = new org.joml.Vector3f(0.1f,0.2f,0.3f);
            LightingPolicy.apply(false,color);
            helper.assertTrue(color.equals(new org.joml.Vector3f(1)),"Normal visual lightmap is uniformly bright");
            color.set(0.1f,0.2f,0.3f); LightingPolicy.apply(true,color);
            helper.assertTrue(color.equals(new org.joml.Vector3f(0.1f,0.2f,0.3f)),"Dark policy preserves vanilla lightmap and player lights");
            LightingPolicy.receive(true);
            helper.assertTrue(LightingPolicy.darkness(),"Remote darkness overrides local bright preference");
            LightingPolicy.disconnect();
            helper.assertTrue(!LightingPolicy.darkness(),"Disconnect restores local preference");
            helper.assertTrue(Content.LIGHT.get().defaultBlockState().getLightEmission(level,new BlockPos(2,64,2))==0,"Decorative light emits no block light");
            Settings.DARKNESS.set(true);
            helper.assertTrue(!generator.original(2,64,2).is(Content.LIGHT.get()) && !generator.original(2,72,2).is(Content.LIGHT.get()),"Dark generation/restoration omits floor and ceiling lamps");
            var lamp = new BlockPos(70,100,70);
            level.setBlockAndUpdate(lamp, net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState());
            helper.assertTrue(level.getBlockState(lamp).getLightEmission(level,lamp) == 14,"Player light remains functional in darkness");
            level.removeBlock(lamp,false);
            var zombie = new net.minecraft.world.entity.monster.Zombie(level);
            Settings.NATURAL_SPAWNS.set(false);
            var natural = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(zombie,level,net.minecraft.world.entity.MobSpawnType.NATURAL,null);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(natural);
            helper.assertTrue(natural.getResult() == net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL,"Natural spawns denied by default");
            var machine = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(zombie,level,net.minecraft.world.entity.MobSpawnType.SPAWNER,null);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(machine);
            helper.assertTrue(machine.getResult() == net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.DEFAULT,"Machine/spawner path unchanged");
            Settings.NATURAL_SPAWNS.set(true);
            var allowed = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(zombie,level,net.minecraft.world.entity.MobSpawnType.NATURAL,null);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(allowed);
            helper.assertTrue(allowed.getResult() == net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.DEFAULT,"Enabled mobs still use vanilla spawn checks");
        } finally { Settings.DARKNESS.set(dark); Settings.NATURAL_SPAWNS.set(mobs); }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Elsebase.ID, timeoutTicks = 400)
    public static void selectiveExplosion(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Elsebase.DIMENSION);
        BlockPos structure = new BlockPos(40, 100, 40), wool = structure.east(), placed = structure.west();
        level.setBlockAndUpdate(structure, Content.structure(RoomLayout.Material.WALL));
        level.setBlockAndUpdate(wool, Blocks.WHITE_WOOL.defaultBlockState());
        level.setBlockAndUpdate(placed, Content.WALL.get().defaultBlockState());
        level.explode(null, 40.5, 101.5, 40.5, 8, Level.ExplosionInteraction.TNT);
        helper.assertTrue(level.getBlockState(structure).is(Content.WALL.get()), "Generated structure survives actual explosion");
        helper.assertTrue(level.getBlockState(wool).isAir(), "Player wool follows ordinary explosion rules");
        helper.assertTrue(level.getBlockState(placed).isAir(), "Same material without provenance remains destructible");
        level.removeBlock(structure, false);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty", templateNamespace = Elsebase.ID, timeoutTicks = 400)
    public static void instantLifecycleAndClaims(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var externalLevel = helper.getLevel();
        for (int x = 3; x <= 13; x++) for (int z = 3; z <= 13; z++) {
            externalLevel.setBlockAndUpdate(new BlockPos(x,149,z), Blocks.STONE.defaultBlockState());
            for (int y = 150; y < 155; y++) externalLevel.setBlockAndUpdate(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState());
        }
        player.teleportTo(externalLevel, 6.5,150,6.5,0,0);
        Portals.summon(player);
        var data = WorldState.get(player.server);
        var first = data.instant(player.getUUID());
        helper.assertTrue(first != null, "Instant access requires no item");
        helper.assertTrue(first.external().blocks().size() == 2, "Doorway occupies exactly two blocks");
        helper.assertTrue(externalLevel.getBlockState(first.external().position().above()).getValue(PortalBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER, "Upper doorway model selected");
        helper.assertTrue(externalLevel.getBlockState(first.external().position().above(2)).isAir(), "No third portal block");
        var home = data.home(player.server, player.getUUID());
        var oldReturn = first.external();
        var innerLevel = player.server.getLevel(Elsebase.DIMENSION);
        var innerCenter = first.inner().center();
        helper.assertTrue(innerLevel.getBlockState(home.anchor()).is(Content.ANCHOR.get()), "Initial personal marker exists before entry");
        innerLevel.removeBlock(home.anchor().below(),false);

        // Exercise actual server crossing, not a direct teleport shortcut.
        var sourceCenter = first.external().center();
        var normal = net.minecraft.world.phys.Vec3.atLowerCornerOf(first.external().facing().getNormal());
        var before = sourceCenter.add(normal.scale(0.2));
        var after = sourceCenter.subtract(normal.scale(0.2));
        var lateral = net.minecraft.world.phys.Vec3.atLowerCornerOf(first.external().right().getNormal()).scale(0.9);
        player.setPos(after.add(lateral)); player.xo = before.x + lateral.x; player.yo = before.y; player.zo = before.z + lateral.z;
        Portals.cross(player, first.external().position());
        helper.assertTrue(player.serverLevel() == externalLevel, "Body outside narrow doorway cannot traverse");
        player.setPos(after.add(0,2.1,0)); player.xo = before.x; player.yo = before.y + 2.1; player.zo = before.z;
        Portals.cross(player, first.external().position());
        helper.assertTrue(player.serverLevel() == externalLevel, "Body above two-block doorway cannot traverse");
        player.setPos(after); player.xo = before.x; player.yo = before.y; player.zo = before.z;
        Portals.cross(player, first.external().position());
        helper.assertTrue(player.serverLevel() == innerLevel, "Crossing the plane changes dimension");
        helper.assertTrue(innerLevel.getBlockState(home.anchor().below()).is(Content.FLOOR.get()), "Entry repairs missing marker support");
        helper.assertTrue(player.blockPosition().equals(home.anchor()), "Initial arrival is on the marker");

        player.teleportTo(innerLevel, innerCenter.x,65,innerCenter.z + 4,180,0);
        Portals.summon(player);
        var recalled = data.instant(player.getUUID());
        helper.assertTrue(recalled.external().equals(oldReturn), "Inside recall preserves external return");
        helper.assertTrue(data.home(player.server,player.getUUID()).reference().equals(home.reference()), "Recall preserves reference");
        helper.assertTrue(!recalled.inner().equals(first.inner()), "Inner endpoint actually moved");
        helper.assertTrue(innerLevel.getBlockState(first.inner().position()).isAir(), "Old inner blocks retired");

        var marker = first.inner().position().offset(3,0,-3);
        Portals.anchor(player, marker);
        var anchored = data.home(player.server, player.getUUID());
        helper.assertTrue(marker.equals(anchored.anchor()), "Reusable tool creates marker");
        helper.assertTrue(!anchored.reference().equals(home.reference()), "Anchor changes reference");
        helper.assertTrue(data.instant(player.getUUID()).external().equals(oldReturn), "Anchor preserves return");

        // Protection listener cancels one placement after another has been staged: both must roll back.
        BlockPos a = first.inner().position().offset(1,4,1), b = a.east();
        var originalA = innerLevel.getBlockState(a); var originalB = innerLevel.getBlockState(b);
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> deny = e -> {
            if (e.getPos().equals(b)) e.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try {
            boolean changed = WorldEdits.apply(player, List.of(
                    new WorldEdits.Change(innerLevel,a,originalA,Content.structure(RoomLayout.Material.WALL)),
                    new WorldEdits.Change(innerLevel,b,originalB,Content.structure(RoomLayout.Material.WALL))));
            helper.assertTrue(!changed, "Claim denial rejects atomic edit");
            helper.assertTrue(innerLevel.getBlockState(a).equals(originalA) && innerLevel.getBlockState(b).equals(originalB), "Entire edit rolled back");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        helper.runAfterDelay(20, () -> {
            var targetNormal = net.minecraft.world.phys.Vec3.atLowerCornerOf(oldReturn.facing().getNormal());
            var arrival = BlockPos.containing(oldReturn.center().add(targetNormal.scale(0.9)));
            var previous = externalLevel.getBlockState(arrival);
            externalLevel.setBlockAndUpdate(arrival,Blocks.STONE.defaultBlockState());
            simulateCross(player,recalled.inner());
            helper.assertTrue(player.serverLevel() == externalLevel && player.position().distanceTo(oldReturn.center()) < 12,"Occupied return finds a safe nearby landing");
            helper.assertTrue(externalLevel.getBlockState(arrival).is(Blocks.STONE),"Return obstruction remains intact");
            externalLevel.setBlockAndUpdate(arrival,previous);
            helper.runAfterDelay(21, () -> {
                for (var pos : recalled.inner().blocks()) innerLevel.removeBlock(pos,false);
                innerLevel.removeBlock(recalled.inner().position().below(),false);
                innerLevel.removeBlock(marker.below(),false);
                simulateCross(player,oldReturn);
                helper.assertTrue(innerLevel.getBlockState(marker.below()).is(Content.FLOOR.get()),"Entry repairs only the marker pedestal");
                helper.assertTrue(innerLevel.getBlockState(recalled.inner().position()).isAir(),"Missing unsupported inner frame is a valid arrival state");
                helper.assertTrue(player.serverLevel() == innerLevel,"Existing exterior still enters after inner recall and anchor move");
                helper.assertTrue(player.blockPosition().equals(marker),"Personal re-entry always uses current anchor, not recalled doorway");
                player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed"));
                // Let asynchronous player/chunk teardown settle before the test server immediately shuts down.
                helper.runAfterDelay(40,helper::succeed);
            });
        });
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty", templateNamespace = Elsebase.ID, timeoutTicks = 400)
    public static void damagedInstantRecovery(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var outside = helper.getLevel();
        var inside = player.server.getLevel(Elsebase.DIMENSION);
        var data = WorldState.get(player.server);
        for (int x=610;x<=630;x++) for (int z=610;z<=630;z++) {
            outside.setBlockAndUpdate(new BlockPos(x,149,z),Blocks.STONE.defaultBlockState());
            for (int y=150;y<155;y++) outside.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        player.teleportTo(outside,616.5,150,616.5,0,0);
        Portals.summon(player);
        var initial = data.instant(player.getUUID());
        helper.assertTrue(initial!=null,"Initial instant pair available");
        var home = data.homes.get(player.getUUID());
        inside.removeBlock(home.anchor().below(),false);
        player.teleportTo(inside,home.anchor().getX()+0.5,home.anchor().getY(),home.anchor().getZ()+0.5,0,0);
        // Prevent automatic entry repair so the leaving path really sees a missing anchor floor.
        inside.removeBlock(home.anchor().below(),false);
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> deny = event -> {
            if (event.getLevel()==inside) event.setCanceled(true);
        };
        inside.removeBlock(initial.inner().position().above(),false);
        outside.setBlockAndUpdate(initial.external().position(),Blocks.CHEST.defaultBlockState());
        outside.removeBlock(initial.external().position().above(),false);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try {
            simulateCross(player,initial.inner());
            helper.assertTrue(player.serverLevel()==outside,"Exit ignores missing anchor floor, denied inner repairs and destroyed exterior");
            helper.assertTrue(inside.getBlockState(home.anchor().below()).isAir(),"Exit performs no anchor repair");
            helper.assertTrue(outside.getBlockState(initial.external().position()).is(Blocks.CHEST),"Recovery preserves construction at former portal");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        // Recalling inside must preserve the damaged outside coordinate without trying to rebuild it.
        player.teleportTo(inside,home.anchor().getX()+0.5,65,home.anchor().getZ()+0.5,180,0);
        Portals.summon(player);
        var recalled = data.instant(player.getUUID());
        helper.assertTrue(recalled.external().equals(initial.external()) && !recalled.inner().equals(initial.inner()),"Recall succeeds independently of blocked exterior");
        // Old-portal break denial cannot block a fresh outside summon.
        player.teleportTo(outside,622.5,150,622.5,180,0);
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> denyOld = event -> {
            if (event.getState().is(Content.PORTAL.get())) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(denyOld);
        try { Portals.summon(player); }
        finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(denyOld); }
        var renewed = data.instant(player.getUUID());
        helper.assertTrue(!renewed.external().equals(initial.external()),"New outside summon replaces stale pair despite denied old-surface cleanup");
        helper.assertTrue(outside.getBlockState(initial.external().position()).is(Blocks.CHEST),"Renewal preserves block entity replacing old surface");
        for (var pos : recalled.inner().blocks()) helper.assertTrue(!inside.getBlockState(pos).is(Content.PORTAL.get()),"Old recalled surfaces retired");
        // If no physical portal can be placed inside, F itself is the escape route.
        player.teleportTo(inside,home.anchor().getX()+0.5,65,home.anchor().getZ()+0.5,180,0);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try { Portals.summon(player); }
        finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        helper.assertTrue(player.serverLevel()==outside,"F escapes when all inner placements are denied");
        data.remove(renewed.id());
        player.teleportTo(inside,home.anchor().getX()+0.5,65,home.anchor().getZ()+0.5,180,0);
        Portals.summon(player);
        helper.assertTrue(player.serverLevel()==outside,"F remembers a safe outside return after pair deletion");
        player.teleportTo(inside,home.anchor().getX()+0.5,65,home.anchor().getZ()+0.5,180,0);
        var removedDimension = new Endpoint(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,Elsebase.id("removed_dimension")),new BlockPos(8,65,8),Direction.NORTH);
        data.rememberReturn(player.getUUID(),removedDimension);
        Portals.summon(player);
        helper.assertTrue(player.serverLevel()==player.server.overworld() && dev.elsebase.portal.ReturnTravel.safe(player,player.serverLevel(),player.position()),"Removed outside dimension recovers safely to overworld spawn");
        data.returns.remove(player.getUUID()); data.setDirty();
        player.teleportTo(inside,home.anchor().getX()+0.5,65,home.anchor().getZ()+0.5,180,0);
        Portals.summon(player);
        helper.assertTrue(player.serverLevel()==player.server.overworld(),"F escapes even without pair or return history");
        var overworld = player.server.overworld();
        var oldSpawn = overworld.getSharedSpawnPos();
        float oldAngle = overworld.getSharedSpawnAngle();
        for (int x=7998;x<=8018;x++) for (int z=7998;z<=8018;z++) {
            overworld.setBlockAndUpdate(new BlockPos(x,149,z),Blocks.MAGMA_BLOCK.defaultBlockState());
            for (int y=150;y<=160;y++) overworld.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        try {
            overworld.setDefaultSpawnPos(new BlockPos(8008,150,8008),0);
            player.teleportTo(inside,home.anchor().getX()+0.5,65,home.anchor().getZ()+0.5,180,0);
            Portals.summon(player);
            helper.assertTrue(player.serverLevel()==overworld && dev.elsebase.portal.ReturnTravel.safe(player,overworld,player.position()),"Unsafe spawn area receives safe empty-space refuge");
            helper.assertTrue(overworld.getBlockState(player.blockPosition().below()).is(Blocks.STONE),"Emergency refuge supplies solid footing");
            helper.assertTrue(overworld.getBlockState(new BlockPos(8008,149,8008)).is(Blocks.MAGMA_BLOCK),"Emergency recovery never clears existing terrain");
        } finally { overworld.setDefaultSpawnPos(oldSpawn,oldAngle); }


        player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed"));
        helper.runAfterDelay(40,helper::succeed);
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty", templateNamespace = Elsebase.ID, timeoutTicks = 400)
    public static void portalContactAndLifetime(GameTestHelper helper) {
        // Isolate traversal from the two other fixtures sharing the global per-tick arrival budget.
        helper.runAfterDelay(5, () -> {
        var player = helper.makeMockServerPlayerInLevel();
        var server = player.server;
        var inside = server.getLevel(Elsebase.DIMENSION);
        var outside = helper.getLevel();
        var data = WorldState.get(server);
        var inner = new Endpoint(Elsebase.DIMENSION,new BlockPos(1032,65,1032),Direction.SOUTH);
        var external = new Endpoint(outside.dimension(),new BlockPos(1032,150,1032),Direction.SOUTH);
        var pair = new PortalPair(UUID.randomUUID(),player.getUUID(),false,inner,external);
        data.put(pair); data.rememberReturn(player.getUUID(),external);
        inside.getChunkAt(inner.position());
        for (int dx=-2;dx<=2;dx++) for (int dz=-2;dz<=2;dz++) {
            outside.setBlockAndUpdate(external.position().offset(dx,-1,dz),Blocks.STONE.defaultBlockState());
            for (int dy=0;dy<=2;dy++) outside.setBlockAndUpdate(external.position().offset(dx,dy,dz),Blocks.AIR.defaultBlockState());
        }
        for (var endpoint : List.of(inner,external)) {
            var level = server.getLevel(endpoint.dimension());
            for (var pos : endpoint.blocks()) level.setBlockAndUpdate(pos,PortalBlock.stateAt(endpoint,pos));
        }
        player.teleportTo(inside,1032.5,64.6,1032.5,0,0);
        inside.removeBlock(inner.position().below(),false);
        inside.removeBlock(data.homes.get(player.getUUID()).anchor().below(),false);
        // Exercise the actual block contact callback while feet are already below the opening.
        inside.getBlockState(inner.position()).entityInside(inside,inner.position(),player);
        helper.assertTrue(player.serverLevel()==outside,"Body contact exits unsupported portal while falling below its foot level");
        var timer = data.lifetimes.get(pair.id());
        long now = server.overworld().getGameTime();
        helper.assertTrue(!timer.inside() && timer.expires()==now+1200,"Successful exit renews the complete outside minute");
        helper.assertTrue(!timer.expired(now+1199) && timer.expired(now+1200),"Exact one-minute boundary");
        // Every successful summon/use refreshes the actual persisted deadline.
        data.lifetimes.put(pair.id(),new dev.elsebase.portal.InstantExpiry.State(now,false));
        dev.elsebase.portal.InstantExpiry.used(player);
        dev.elsebase.portal.InstantExpiry.tick(server);
        helper.assertTrue(data.pairs.containsKey(pair.id()),"Refreshed pair cannot expire at its old deadline");
        player.teleportTo(inside,1034.5,65,1034.5,0,0);
        data.lifetimes.put(pair.id(),new dev.elsebase.portal.InstantExpiry.State(0,true));
        dev.elsebase.portal.InstantExpiry.tick(server);
        helper.assertTrue(data.pairs.containsKey(pair.id()),"No timeout while owner is inside");
        var loaded = WorldState.load(data.save(new CompoundTag(),inside.registryAccess()),inside.registryAccess());
        helper.assertTrue(loaded.lifetimes.get(pair.id()).equals(data.lifetimes.get(pair.id())),"Deadline and inside residence survive persistence");
        player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed"));
        dev.elsebase.portal.InstantExpiry.tick(server);
        helper.assertTrue(data.pairs.containsKey(pair.id()),"Offline inside owner retains return");
        // An offline outside owner's expired pair is removed without touching replacement construction/history.
        data.lifetimes.put(pair.id(),new dev.elsebase.portal.InstantExpiry.State(0,false));
        outside.setBlockAndUpdate(external.position().above(),Blocks.CHEST.defaultBlockState());
        dev.elsebase.portal.InstantExpiry.tick(server);
        helper.assertTrue(data.instant(player.getUUID())==null && !data.lifetimes.containsKey(pair.id()),"Outside expiry removes pair and lifetime");
        helper.assertTrue(inside.getBlockState(inner.position()).isAir() && outside.getBlockState(external.position()).isAir(),"Expiry retires loaded surfaces on both ends");
        helper.assertTrue(outside.getBlockState(external.position().above()).is(Blocks.CHEST) && data.returns.get(player.getUUID()).equals(external),"Expiry preserves construction and recovery coordinates");
        // A supported marker without a supported doorway remains a valid personal arrival, not an error state.
        helper.runAfterDelay(40,helper::succeed);
        });
    }

    private static void simulateCross(net.minecraft.server.level.ServerPlayer player,Endpoint endpoint) {
        var normal = net.minecraft.world.phys.Vec3.atLowerCornerOf(endpoint.facing().getNormal());
        var before = endpoint.center().add(normal.scale(0.2));
        player.setPos(endpoint.center().subtract(normal.scale(0.2)));
        player.xo = before.x; player.yo = before.y; player.zo = before.z;
        Portals.cross(player,endpoint.position());
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty", templateNamespace = Elsebase.ID, timeoutTicks = 400)
    public static void toolsLevelsAndAnchorTickets(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var level = player.server.getLevel(Elsebase.DIMENSION);
        var data = WorldState.get(player.server);
        var initial = data.home(player.server,player.getUUID()).anchor();
        helper.assertTrue(anchorTicket(level,initial),"Online owner holds initial anchor ticket");
        player.teleportTo(level,200.5,65,200.5,0,0);
        var marker = new BlockPos(200,65,201);
        Portals.anchor(player,marker);
        helper.assertTrue(data.homes.get(player.getUUID()).anchor().equals(marker),"Anchor moved to selected level");
        helper.assertTrue(!anchorTicket(level,initial) && anchorTicket(level,marker),"Moving anchor transfers ticket");
        level.removeBlock(marker.below(),false);
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> denyRepair = event -> {
            if (event.getPos().equals(marker.below())) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(denyRepair);
        try {
            helper.assertTrue(!Anchors.ensure(player) && level.getBlockState(marker.below()).isAir(),"Denied safety repair does not bypass claims");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(denyRepair); }
        helper.assertTrue(Anchors.ensure(player) && level.getBlockState(marker.below()).is(Content.FLOOR.get()),"Missing marker floor is restored when allowed");

        var floor = new BlockPos(200,64,200);
        var ceiling = floor.above(8);
        var remove = new net.minecraft.world.item.ItemStack(Content.REMOVAL_TOOL.get());
        var create = new net.minecraft.world.item.ItemStack(Content.CREATION_TOOL.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,remove);
        player.setXRot(-90);
        Content.REMOVAL_TOOL.get().use(level,player,net.minecraft.world.InteractionHand.MAIN_HAND);
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        helper.assertTrue(level.getBlockState(ceiling).isAir() && !level.getBlockState(ceiling.above(8)).isAir(),"Air use removes the looked-at current ceiling only");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,create);
        player.setShiftKeyDown(true);
        Content.CREATION_TOOL.get().use(level,player,net.minecraft.world.InteractionHand.MAIN_HAND);
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        player.setShiftKeyDown(false);
        helper.assertTrue(StructuralBlock.protectedStructure(level,level.getBlockState(ceiling)),"Creation tool restores current ceiling");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,remove);
        player.getCooldowns().removeCooldown(Content.REMOVAL_TOOL.get());
        var unrelatedHit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor),Direction.UP,floor,false);
        var consumed = Content.REMOVAL_TOOL.get().onItemUseFirst(remove,new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,unrelatedHit));
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        helper.assertTrue(consumed.consumesAction() && level.getBlockState(ceiling).isAir() && !level.getBlockState(floor).isAir(),"Block use takes priority and selects by gaze, not clicked coordinates");


        player.setXRot(90);
        dev.elsebase.structure.StructuralEditor.request(player,false);
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        helper.assertTrue(!level.getBlockState(floor).isAir() && !level.getBlockState(marker.below()).isAir(),"Anchor rejects entire floor removal");
        player.teleportTo(level,200.5,57,200.5,0,0);
        player.setXRot(-90);
        dev.elsebase.structure.StructuralEditor.request(player,false);
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        helper.assertTrue(!level.getBlockState(marker.below()).isAir(),"Anchor floor also protected when targeted as lower ceiling");

        for (var item : new ToolItem[]{Content.ANCHOR_TOOL.get(),Content.PORTAL_TOOL.get(),Content.REMOVAL_TOOL.get(),Content.CREATION_TOOL.get()}) {
            var lines = new ArrayList<net.minecraft.network.chat.Component>();
            item.appendHoverText(new net.minecraft.world.item.ItemStack(item),net.minecraft.world.item.Item.TooltipContext.EMPTY,lines,net.minecraft.world.item.TooltipFlag.NORMAL);
            helper.assertTrue(!lines.isEmpty(),"Every tool supplies a tooltip");
        }
        player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed"));
        helper.assertTrue(!anchorTicket(level,marker),"Logout releases anchor ticket immediately");
        helper.runAfterDelay(40,helper::succeed);
    }
    private static void aim(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.phys.Vec3 target) {
        var delta = target.subtract(player.getEyePosition());
        player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));
    }
    private static boolean anchorTicket(net.minecraft.server.level.ServerLevel level,BlockPos position) {
        var tickets = level.getChunkSource().chunkMap.getDistanceManager().tickets.get(new net.minecraft.world.level.ChunkPos(position).toLong());
        return tickets != null && tickets.stream().anyMatch(t -> t.getType()==Anchors.TICKET);
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty", templateNamespace = Elsebase.ID, timeoutTicks = 400)
    public static void independentPanelsAndReplaceablePortals(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var inner = player.server.getLevel(Elsebase.DIMENSION);
        var outside = helper.getLevel();
        // Work far from the other fixtures. Exercise each of the four independent room walls.
        player.teleportTo(inner,264.5,65,264.5,0,0);
        for (var side : new Direction[]{Direction.WEST,Direction.EAST,Direction.NORTH,Direction.SOUTH}) {
            var panel = new dev.elsebase.structure.StructuralEditor.Panel(16,16,side,64);
            for (var pos : panel.positions(false)) inner.setBlockAndUpdate(pos,Content.structure(RoomLayout.Material.WALL));
            var target = panel.positions(false).get(20);
            var opposite = target.relative(side);
            inner.setBlockAndUpdate(opposite,Content.structure(RoomLayout.Material.WALL));
            aim(player,net.minecraft.world.phys.Vec3.atCenterOf(target));
            dev.elsebase.structure.StructuralEditor.request(player,false);
            dev.elsebase.structure.StructuralEditor.tick(player.server);
            helper.assertTrue(inner.getBlockState(target).isAir() && inner.getBlockState(opposite).is(Content.WALL.get()),"Removal affects only targeted wall half: " + side);
            dev.elsebase.structure.StructuralEditor.request(player,true);
            dev.elsebase.structure.StructuralEditor.tick(player.server);
            helper.assertTrue(panel.positions(false).stream().allMatch(pos -> inner.getBlockState(pos).is(Content.WALL.get())),"Creation builds solid wall including seeded openings: " + side);
        }
        var floorTarget = new BlockPos(264,64,264);
        player.setXRot(90);
        dev.elsebase.structure.StructuralEditor.request(player,false);
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        helper.assertTrue(inner.getBlockState(floorTarget).isAir(),"Interior floor removed");
        for (int n=0;n<16;n++) for (var pos : List.of(new BlockPos(256,64,256+n),new BlockPos(271,64,256+n),
                new BlockPos(256+n,64,256),new BlockPos(256+n,64,271)))
            helper.assertTrue(inner.getBlockState(pos).is(Content.BORDER.get()),"Floor removal preserves all supporting borders");

        // Establish a known clear outdoor pad and cover all summon candidates with replaceable snow.
        for (int x=290;x<=302;x++) for (int z=290;z<=302;z++) {
            outside.setBlockAndUpdate(new BlockPos(x,149,z),Blocks.STONE.defaultBlockState());
            for (int y=150;y<=153;y++) outside.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            outside.setBlockAndUpdate(new BlockPos(x,150,z),Blocks.SNOW.defaultBlockState());
        }
        player.teleportTo(outside,296.5,150,294.5,0,0);
        var vegetation = new BlockPos(296,150,296);
        outside.setBlockAndUpdate(vegetation,Blocks.SHORT_GRASS.defaultBlockState());
        var testEndpoint = new Endpoint(outside.dimension(),vegetation,Direction.NORTH);
        helper.assertTrue(Portals.canFit(player,testEndpoint,null),"Minecraft replaceable snow and grass accepted");
        outside.setBlockAndUpdate(vegetation,Blocks.CHEST.defaultBlockState());
        helper.assertTrue(!Portals.canFit(player,testEndpoint,null),"Block entities are not replaceable portal terrain");
        outside.setBlockAndUpdate(vegetation,Blocks.STONE.defaultBlockState());
        helper.assertTrue(!Portals.canFit(player,testEndpoint,null),"Solid terrain retained");
        outside.setBlockAndUpdate(vegetation,Blocks.SHORT_GRASS.defaultBlockState());
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> deny = event -> {
            if (event.getLevel()==outside && event.getState().is(Blocks.SNOW)) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try {
            Portals.summon(player);
            helper.assertTrue(WorldState.get(player.server).instant(player.getUUID())==null,"Denied replaceable clearing refuses pair creation");
            helper.assertTrue(outside.getBlockState(vegetation).is(Blocks.SHORT_GRASS),"Denied transaction preserves vegetation");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        Portals.summon(player);
        var pair = WorldState.get(player.server).instant(player.getUUID());
        helper.assertTrue(pair != null && pair.external().position().equals(vegetation),"Summon replaces grass at preferred doorway");
        helper.assertTrue(outside.getBlockState(vegetation).is(Content.PORTAL.get()),"Grass replaced with frame");
        helper.assertTrue(outside.getBlockState(vegetation.north()).isAir() && outside.getBlockState(vegetation.south()).isAir(),"Snow cleared on both approaches");
        helper.assertTrue(pair.external().blocks().size()==2,"Portal itself remains one wide and two high");
        player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed"));
        helper.runAfterDelay(40,helper::succeed);
    }

    @SuppressWarnings("removal")
    @GameTest(template = "empty", templateNamespace = Elsebase.ID, timeoutTicks = 400)
    public static void gazeSelectionAndBorderRepair(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var level = player.server.getLevel(Elsebase.DIMENSION);
        player.teleportTo(level,-55.5,65,-55.5,0,0);
        level.getChunk(-4,-4);
        var eye = player.getEyePosition();
        for (Direction side : Direction.values()) {
            var look = net.minecraft.world.phys.Vec3.atLowerCornerOf(side.getNormal());
            var panel = dev.elsebase.structure.PanelSelection.select(level,player.position(),eye,look,true);
            helper.assertTrue(panel!=null && panel.cellX()==-4 && panel.cellZ()==-4 && panel.side()==side,"All six geometric surfaces at negative coordinates: " + side + ", actual=" + panel + ", feet=" + player.position());
            helper.assertTrue(panel.positions(true).size()<257,"Creation fits atomic edit budget");
            if (side.getAxis()==Direction.Axis.Y) helper.assertTrue(panel.positions(false).size()==196 && panel.positions(true).size()==256,"Removal preserves rim; creation includes it");
        }
        var west = new dev.elsebase.structure.StructuralEditor.Panel(-4,-4,Direction.WEST,64);
        var neighbor = new dev.elsebase.structure.StructuralEditor.Panel(-5,-4,Direction.EAST,64);
        level.getChunk(-5,-4);
        for (var pos : west.positions(false)) level.removeBlock(pos,false);
        for (var pos : neighbor.positions(false)) level.setBlockAndUpdate(pos,Content.structure(RoomLayout.Material.WALL));
        var westLook = new net.minecraft.world.phys.Vec3(-1,0,0);
        helper.assertTrue(dev.elsebase.structure.PanelSelection.select(level,player.position(),eye,westLook,false).equals(neighbor),"Removal reaches only directly exposed neighbor half");
        helper.assertTrue(dev.elsebase.structure.PanelSelection.select(level,player.position(),eye,westLook,true).equals(west),"Creation targets missing local wall first");
        for (int y : new int[]{64,72}) for (int z=-64;z<-48;z++) level.removeBlock(new BlockPos(-64,y,z),false);
        aim(player,new net.minecraft.world.phys.Vec3(-64,eye.y,-55.5));
        dev.elsebase.structure.StructuralEditor.request(player,true);
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        helper.assertTrue(west.positions(false).stream().allMatch(pos -> level.getBlockState(pos).is(Content.WALL.get())),"Wall restored without any supporting border row");
        for (Direction side : new Direction[]{Direction.DOWN,Direction.UP}) {
            player.setXRot(side==Direction.UP ? -90 : 90);
            dev.elsebase.structure.StructuralEditor.request(player,true);
            dev.elsebase.structure.StructuralEditor.tick(player.server);
            int y = side==Direction.UP ? 72 : 64;
            for (int z=-64;z<-48;z++) helper.assertTrue(level.getBlockState(new BlockPos(-64,y,z)).is(Content.BORDER.get()),"Creation repairs manually removed perimeter");
            dev.elsebase.structure.StructuralEditor.request(player,false);
            dev.elsebase.structure.StructuralEditor.tick(player.server);
            helper.assertTrue(level.getBlockState(new BlockPos(-64,y,-55)).is(Content.BORDER.get()),"Removal retains repaired walkway");
        }
        helper.assertTrue(!dev.elsebase.structure.PanelSelection.withinReach(player.position(),new dev.elsebase.structure.StructuralEditor.Panel(-5,-4,Direction.WEST,64)),"Neighbor far wall excluded");
        helper.assertTrue(!dev.elsebase.structure.PanelSelection.withinReach(player.position(),new dev.elsebase.structure.StructuralEditor.Panel(-5,-4,Direction.UP,64)),"Neighbor ceiling excluded");
        helper.assertTrue(!dev.elsebase.structure.PanelSelection.withinReach(player.position(),new dev.elsebase.structure.StructuralEditor.Panel(-5,-5,Direction.EAST,64)),"Diagonal room excluded");
        player.setXRot(0); player.setYRot(90);
        dev.elsebase.structure.StructuralEditor.request(player,false);
        player.teleportTo(level,-23.5,65,-55.5,0,0);
        dev.elsebase.structure.StructuralEditor.tick(player.server);
        helper.assertTrue(west.positions(false).stream().allMatch(pos -> level.getBlockState(pos).is(Content.WALL.get())),"Queued edit rejected after leaving permitted room range");
        player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed"));
        helper.runAfterDelay(40,helper::succeed);
    }

    @GameTest(template = "empty", templateNamespace = Elsebase.ID, timeoutTicks = 200)
    public static void dimensionAndStructure(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Elsebase.DIMENSION);
        helper.assertTrue(level != null, "Backdoor registered");
        var generator = (dev.elsebase.world.RoomGenerator) level.getChunkSource().getGenerator();
        helper.assertTrue(generator.original(8,0,8).is(Blocks.BEDROCK), "Generator uses actual vanilla bedrock at bottom");
        helper.assertTrue(generator.original(8,1,8).isAir(), "Bottom room starts directly above bedrock");
        var floor = new BlockPos(8, RoomLayout.FLOOR, 8);
        helper.assertTrue(StructuralBlock.protectedStructure(level, level.getBlockState(floor)), "Generated floor protected");
        helper.assertTrue(level.getBlockState(floor.above()).isAir(), "Interior clear");
        helper.assertTrue(!level.dimensionType().hasSkyLight() && level.dimensionType().ambientLight() == 0, "No ambient or skylight");
        var placed = Content.WALL.get().defaultBlockState();
        helper.assertTrue(!StructuralBlock.protectedStructure(level, placed), "Player variant unprotected");
        helper.assertTrue(!StructuralBlock.protectedStructure(helper.getLevel(), placed.setValue(StructuralBlock.STRUCTURAL, true)), "Other dimension unchanged");
        var mined = floor.above(2);
        level.setBlockAndUpdate(mined, Content.structure(RoomLayout.Material.WALL));
        helper.assertTrue(level.destroyBlock(mined, false), "Machine-style deliberate mining allowed");
        helper.assertTrue(level.getBlockState(mined).isAir(), "Structural block removed by mining");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Elsebase.ID)
    public static void doorwayReconciliation(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = WorldState.get(level.getServer());
        var outside = new Endpoint(level.dimension(),new BlockPos(88,150,88),Direction.SOUTH);
        var inside = new Endpoint(Elsebase.DIMENSION,new BlockPos(88,65,88),Direction.NORTH);
        var pair = new PortalPair(UUID.randomUUID(),UUID.randomUUID(),true,inside,outside);
        data.put(pair);
        var chunk = level.getChunkAt(outside.position());
        var neighbor = outside.position().east();
        level.setBlockAndUpdate(neighbor,Blocks.CHEST.defaultBlockState());
        for (var pos : outside.blocks()) level.setBlockAndUpdate(pos,Content.PORTAL.get().defaultBlockState());
        var orphan = outside.position().above(3);
        level.setBlockAndUpdate(orphan,Content.PORTAL.get().defaultBlockState());
        var hook = new ServerEvents();
        hook.chunkLoaded(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk,false));
        for (var pos : outside.blocks()) helper.assertTrue(level.getBlockState(pos).equals(PortalBlock.stateAt(outside,pos)),"Retained doorway receives correct half and facing");
        helper.assertTrue(level.getBlockState(orphan).isAir(),"Unregistered portal block removed");
        helper.assertTrue(level.getBlockState(neighbor).is(Blocks.CHEST),"Reconciliation preserves player construction");
        hook.chunkLoaded(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk,false));
        helper.assertTrue(data.at(level.dimension(),outside.position()).equals(pair),"Repeated reconciliation preserves ownership");
        for (var pos : outside.blocks()) level.removeBlock(pos,false);
        level.removeBlock(neighbor,false); data.remove(pair.id());
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Elsebase.ID)
    public static void savedPortalRoundTrip(GameTestHelper helper) {
        var state = new WorldState();
        UUID owner = new UUID(12, 34);
        var inner = new Endpoint(Elsebase.DIMENSION, new BlockPos(8,65,8), Direction.SOUTH);
        var outside = new Endpoint(Level.OVERWORLD, new BlockPos(108,65,108), Direction.NORTH);
        var pair = new PortalPair(new UUID(56,78), owner, false, inner, outside);
        state.put(pair);
        state.rememberReturn(owner,outside);
        var home = state.home(helper.getLevel().getServer(), owner);
        var registries = helper.getLevel().registryAccess();
        var loaded = WorldState.load(state.save(new CompoundTag(), registries), registries);
        helper.assertTrue(pair.equals(loaded.instant(owner)), "Pair, owner, return and facing survive reload");
        helper.assertTrue(home.equals(loaded.home(helper.getLevel().getServer(), owner)), "Reservation survives reload");
        helper.assertTrue(loaded.at(Level.OVERWORLD,outside.position()).equals(pair), "Endpoint index restored");
        for (int unsupported : new int[]{1,2,3,5}) {
            var invalid = state.save(new CompoundTag(),registries); invalid.putInt("version",unsupported);
            boolean refused = false;
            try { WorldState.load(invalid,registries); }
            catch (IllegalStateException expected) { refused = true; }
            helper.assertTrue(refused, "Unsupported save version rejected without migration");
        }
        boolean overlapRejected = false;
        try { loaded.put(new PortalPair(UUID.randomUUID(),UUID.randomUUID(),true,inner,outside)); }
        catch (IllegalStateException expected) { overlapRejected = true; }
        helper.assertTrue(overlapRejected && loaded.pairs.size() == 1 && loaded.at(Level.OVERWORLD,outside.position()).equals(pair), "Rejected overlap leaves registry intact");
        loaded.remove(pair.id());
        helper.assertTrue(loaded.returns.get(owner).equals(outside),"Last outside return survives reload and pair removal");
        helper.assertTrue(loaded.at(Level.OVERWORLD,outside.position()) == null, "Removed pair no longer indexed");
        boolean rejected = false;
        try { new PortalPair(UUID.randomUUID(),owner,true,outside,outside); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "External-to-external topology rejected");
        helper.succeed();
    }
}
