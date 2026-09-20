package dev.elsebase.template;

import dev.elsebase.*;
import dev.elsebase.portal.WorldState;
import dev.elsebase.structure.StructuralEditor.Panel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;

/** Real-registry tests for independent shared-slab faces, offline defaults and material/loot policy. */
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TemplateIntegrationTests {
    @GameTest(template="empty",templateNamespace=Elsebase.ID)
    public static void newThemeDraftLifecycle(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var state=TemplateState.get(player.server); int count=state.entries.size(); String saved="";
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Content.SCANNER.get()));
        try {
            TemplateScanner.create(player,"New design",false); var draft=TemplateScanner.draft(player);
            helper.assertTrue(draft.base().isEmpty() && draft.unsaved() && state.entries.size()==count,"New theme is a private, unpublished draft");
            helper.assertTrue(draft.theme().wall().at(0,0).block().equals("minecraft:stone") && draft.theme().floor().equals(draft.theme().ceiling()),"New themes contain three neutral surfaces");
            boolean rejected=false; try { TemplateScanner.create(player,"Replacement",false); } catch(Pattern.Rejected expected) { rejected=true; }
            helper.assertTrue(rejected && TemplateScanner.draft(player).equals(draft),"New draft requires confirmation before replacing an unsaved draft");
            rejected=false; try { TemplateScanner.create(player,"",true); } catch(Pattern.Rejected expected) { rejected=true; }
            helper.assertTrue(rejected && TemplateScanner.draft(player).equals(draft),"Invalid new name cannot destroy previous buffer");
            var loaded=TemplateState.load(state.save(new net.minecraft.nbt.CompoundTag(),player.server.registryAccess()),player.server.registryAccess());
            helper.assertTrue(loaded.scans.get(player.getUUID()).equals(draft),"Unpublished new draft survives saving");
            TemplateServer.request(player,new TemplateProtocol.Request("save_scan","","New design".getBytes(java.nio.charset.StandardCharsets.UTF_8))); saved=TemplateScanner.selected(player);
            helper.assertTrue(saved.startsWith("player/"+player.getUUID()+"/") && state.entries.size()==count+1 && !TemplateScanner.draft(player).unsaved(),"New draft publishes as one owned complete theme");
            var panel=new Panel(112,112,Direction.NORTH,64); state.bind(panel,saved); state.defaults.put(player.getUUID(),saved);
            state.choices.computeIfAbsent(player.getUUID(),key -> new EnumMap<>(ToolItem.Kind.class)).put(ToolItem.Kind.PAINT,saved);
            boolean protectedBuiltin=false; try { TemplateServer.delete(player,TemplateState.builtin("quiet_workshop")); } catch(Pattern.Rejected expected) { protectedBuiltin=true; }
            helper.assertTrue(protectedBuiltin,"Built-ins cannot be deleted");
            var owned=state.entries.get(saved); state.entries.put(saved,new TemplateState.Entry(saved,UUID.randomUUID().toString(),owned.theme()));
            boolean protectedForeign=false; try { TemplateServer.delete(player,saved); } catch(Pattern.Rejected expected) { protectedForeign=true; }
            helper.assertTrue(protectedForeign && state.entries.containsKey(saved),"Another owner's theme cannot be deleted");
            state.entries.put(saved,owned);
            TemplateServer.delete(player,saved);
            helper.assertTrue(!state.entries.containsKey(saved) && !state.defaults.containsKey(player.getUUID()) && TemplateServer.selection(player,ToolItem.Kind.PAINT).isEmpty(),"Deletion clears owned definition, defaults and tool choice");
            helper.assertTrue(TemplateScanner.draft(player).base().isEmpty() && TemplateScanner.draft(player).unsaved(),"Deletion retains private scanner contents as unpublished draft");
            helper.assertTrue(Arrays.stream(state.column(player.server,112,112)).noneMatch(face -> face.startsWith("player/")),"Deleted explicit binding resolves to defaults");
            helper.assertTrue(!java.nio.file.Files.exists(player.server.getServerDirectory().resolve("elsebase/templates").resolve(saved.replace('/','_')+".json")),"Deleted theme cannot reload from server library");
        } catch(java.io.IOException error) { throw new IllegalStateException(error);
        } finally {
            if(saved.startsWith("player/")) { state.entries.remove(saved); try { java.nio.file.Files.deleteIfExists(player.server.getServerDirectory().resolve("elsebase/templates").resolve(saved.replace('/','_')+".json")); } catch(java.io.IOException error) { throw new IllegalStateException(error); } }
            state.scans.remove(player.getUUID()); state.changed(); TemplateServer.logout(player.getUUID()); player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed"));
        }
        helper.succeed();
    }
    @GameTest(template="empty",templateNamespace=Elsebase.ID)
    public static void authoritativeImportAndClaimPolicy(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var server=player.server; var state=TemplateState.get(server);
        String id="player/"+player.getUUID()+"/00000000-0000-0000-0000-000000000001";
        var beforePattern=new Pattern("Import fixture","Test",1,1,List.of(Materials.describe(Blocks.STONE.defaultBlockState())),List.of(0));
        var afterPattern=new Pattern("Import fixture","Test",1,1,List.of(Materials.describe(Blocks.GOLD_BLOCK.defaultBlockState())),List.of(0));
        var before=new Theme("Import fixture","Test",beforePattern,beforePattern,beforePattern);
        var after=new Theme("Import fixture","Test",afterPattern,afterPattern,afterPattern);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Content.SCANNER.get()));
        try {
            state.entries.put(id,new TemplateState.Entry(id,UUID.randomUUID().toString(),before)); TemplateServer.logout(player.getUUID());
            TemplateServer.request(player,new TemplateProtocol.Request("import",id,after.bytes())); TemplateServer.tick(server);
            helper.assertTrue(state.entries.get(id).theme().equals(before),"Import cannot overwrite another owner");
            state.entries.put(id,new TemplateState.Entry(id,player.getUUID().toString(),before)); TemplateServer.logout(player.getUUID());
            TemplateServer.request(player,new TemplateProtocol.Request("import",id,after.bytes())); TemplateServer.tick(server);
            helper.assertTrue(state.entries.get(id).theme().equals(after),"Authenticated owner can overwrite a valid template");
            var path=server.getServerDirectory().resolve("elsebase/templates").resolve(id.replace('/','_')+".json");
            helper.assertTrue(Theme.readFile(path).equals(after),"Accepted import is atomically persisted in root library"); java.nio.file.Files.delete(path);
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),server.registryAccess());
            try { var packet=new TemplateProtocol.Request("import",id,after.bytes()); TemplateProtocol.Request.CODEC.encode(buffer,packet); var decoded=TemplateProtocol.Request.CODEC.decode(buffer); helper.assertTrue(decoded.key().equals(id) && Theme.parse(decoded.data()).equals(after),"Import envelope survives actual registry-friendly codec"); } finally { buffer.release(); }
            var pos=helper.absolutePos(new BlockPos(1,1,1)); helper.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> deny=event -> { if(event.getPos().equals(pos)) event.setCanceled(true); };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
            try { helper.assertTrue(!WorldEdits.authorizeAppearance(player,List.of(pos)),"Claim denial protects appearance bindings even on an empty face"); }
            finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        } catch(java.io.IOException error) { throw new IllegalStateException(error); }
        finally { state.entries.remove(id); state.changed(); TemplateServer.logout(player.getUUID()); player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed")); }
        helper.succeed();
    }
    @GameTest(template="empty",templateNamespace=Elsebase.ID)
    public static void scannerIsNonDestructive(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var server=player.server; var level=server.getLevel(Elsebase.DIMENSION); level.getChunk(100,100);
        var state=TemplateState.get(server); String base=TemplateState.builtin("arcane_archive"),saved="";
        player.teleportTo(level,1608.5,65,1608.5,180,0); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Content.SCANNER.get()));
        try {
            TemplateScanner.select(player,base,false); var original=TemplateScanner.draft(player).theme();
            // Different positions on all six surfaces prove complete grids and all orientation mappings.
            for(var side:Direction.values()) {
                var panel=new Panel(100,100,side,64);
                for(var pos:panel.positions(true)) { var address=SurfaceAddress.at(pos,side.getOpposite()); level.setBlockAndUpdate(pos,address.u()==2 && address.v()==3?Blocks.GOLD_BLOCK.defaultBlockState():address.u()==5?Blocks.BLUE_CONCRETE.defaultBlockState():Blocks.STONE.defaultBlockState()); }
                var captured=TemplateScanner.capture(player,panel,original.face(side));
                helper.assertTrue(captured.at(2,3).block().equals("minecraft:gold_block") && captured.at(5,1).block().equals("minecraft:blue_concrete") && captured.at(0,0).block().equals("minecraft:stone"),"Asymmetric multi-block layout retained for "+side);
            }
            var north=new Panel(100,100,Direction.NORTH,64); var hole=new BlockPos(1605,66,1600); level.setBlockAndUpdate(hole,Blocks.AIR.defaultBlockState());
            TemplateScanner.scan(player); var draft=TemplateScanner.draft(player);
            helper.assertTrue(draft.changed().equals(Set.of("wall")) && draft.theme().floor().equals(original.floor()) && draft.theme().ceiling().equals(original.ceiling()),"One scan changes only the buffered role");
            helper.assertTrue(state.entries.get(base).theme().equals(original),"Scanning does not publish edits");
            var persisted=TemplateState.load(state.save(new net.minecraft.nbt.CompoundTag(),server.registryAccess()),server.registryAccess());
            helper.assertTrue(persisted.scans.get(player.getUUID()).equals(draft),"Unsaved full-theme buffer survives world save");
            helper.assertTrue(level.getBlockState(new BlockPos(1601,65,1600)).is(Blocks.STONE) && level.getBlockState(hole).isAir(),"Source blocks and doorway remain unchanged");
            var uv=SurfaceAddress.at(hole,Direction.SOUTH); helper.assertTrue(draft.theme().wall().at(uv.u(),uv.v()).equals(original.wall().at(uv.u(),uv.v())),"Empty door cells retain buffered pattern");
            var bad=new BlockPos(1602,66,1600); level.setBlockAndUpdate(bad,Blocks.GLASS.defaultBlockState()); TemplateScanner.scan(player);
            helper.assertTrue(TemplateScanner.draft(player).equals(draft) && level.getBlockState(bad).is(Blocks.GLASS),"Unsupported block rejects complete scan without losing buffer");
            boolean rejected=false; try { TemplateScanner.select(player,TemplateState.builtin("quiet_workshop"),false); } catch(Pattern.Rejected expected) { rejected=true; }
            helper.assertTrue(rejected,"Switching themes cannot silently discard dirty buffer");
            TemplateServer.request(player,new TemplateProtocol.Request("save_scan",base,"My scanned archive".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            saved=TemplateScanner.selected(player);
            helper.assertTrue(saved.startsWith("player/"+player.getUUID()+"/") && state.entries.get(base).theme().equals(original),"Saving built-in creates personal copy");
            helper.assertTrue(TemplateScanner.draft(player).changed().isEmpty(),"Successful save clears dirty status");
            var firstSaved=state.entries.get(saved).theme(); player.setXRot(-90); TemplateScanner.scan(player); player.setXRot(0);
            helper.assertTrue(TemplateScanner.draft(player).changed().equals(Set.of("ceiling")) && state.entries.get(saved).theme().equals(firstSaved),"Owned ceiling edit remains private until save");
            TemplateServer.logout(player.getUUID());
            TemplateServer.request(player,new TemplateProtocol.Request("save_scan",saved,"My scanned archive".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            helper.assertTrue(TemplateScanner.selected(player).equals(saved) && state.entries.get(saved).theme().wall().equals(firstSaved.wall()) && state.entries.get(saved).theme().floor().equals(firstSaved.floor()) && !state.entries.get(saved).theme().ceiling().equals(firstSaved.ceiling()),"Saving owned buffer updates ceiling in place and preserves complex wall/floor patterns");
            for(var pos:north.positions(true)) level.setBlockAndUpdate(pos,Content.structure(dev.elsebase.world.RoomLayout.Material.WALL)); level.setBlockAndUpdate(hole,Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(bad,Blocks.STONE.defaultBlockState());
            TemplateServer.logout(player.getUUID()); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Content.PAINT_TOOL.get()));
            var untouched=state.column(server,100,100); TemplateServer.paint(player);
            helper.assertTrue(TemplateServer.selection(player,ToolItem.Kind.PAINT).isEmpty() && Arrays.equals(untouched,state.column(server,100,100)),"Unselected Paint opens selection without binding a default theme");
            TemplateServer.request(player,new TemplateProtocol.Request("select",saved,new byte[0])); TemplateServer.paint(player);
            helper.assertTrue(state.column(server,100,100)[8*6+Direction.NORTH.ordinal()].equals(saved+"#wall"),"Paint uses selected complete theme");
            helper.assertTrue(level.getBlockState(hole).isAir() && level.getBlockState(bad).is(Blocks.STONE),"Paint neither builds holes nor changes player blocks");
            helper.assertTrue(TemplateServer.selection(player,ToolItem.Kind.CREATE).equals(TemplateState.builtin("quiet_workshop")),"Paint and construction keep independent selections");
            var savedTools=TemplateState.load(state.save(new net.minecraft.nbt.CompoundTag(),server.registryAccess()),server.registryAccess());
            helper.assertTrue(saved.equals(savedTools.choices.get(player.getUUID()).get(ToolItem.Kind.PAINT)),"Tool selection survives world save");
        } finally {
            if(saved.startsWith("player/")) { state.entries.remove(saved); try { java.nio.file.Files.deleteIfExists(server.getServerDirectory().resolve("elsebase/templates").resolve(saved.replace('/','_')+".json")); } catch(java.io.IOException error) { throw new IllegalStateException(error); } }
            state.bindings.remove(net.minecraft.world.level.ChunkPos.asLong(100,100)); state.scans.remove(player.getUUID()); state.choices.remove(player.getUUID()); state.changed(); TemplateServer.logout(player.getUUID()); player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed"));
        }
        helper.succeed();
    }
    @GameTest(template="empty",templateNamespace=Elsebase.ID)
    public static void templateFacesAndPersistence(GameTestHelper helper) {
        var state=new TemplateState(); var server=helper.getLevel().getServer();
        var pattern=new Pattern("Stone","Fixture",1,1,List.of(Materials.describe(Blocks.STONE.defaultBlockState())),List.of(0));
        state.entries.put("test/stone",new TemplateState.Entry("test/stone","",new Theme("Stone","Fixture",pattern,pattern,pattern)));
        state.bind(new Panel(-2,3,Direction.UP,64),"test/stone");
        var pos=new BlockPos(-25,72,54); var lower=SurfaceAddress.at(pos,Direction.DOWN); var upper=SurfaceAddress.at(pos,Direction.UP);
        helper.assertTrue(lower.panel().floor()==64 && lower.panel().side()==Direction.UP,"Bottom slab face belongs to lower ceiling");
        helper.assertTrue(upper.panel().floor()==72 && upper.panel().side()==Direction.DOWN,"Top slab face belongs to upper floor");
        var faces=state.column(server,-2,3); helper.assertTrue(faces[lower.index()].equals("test/stone#ceiling") && !faces[upper.index()].equals("test/stone#ceiling"),"Shared slab does not mix styles");
        for(int index=0;index<96;index++) helper.assertTrue(faces[index].equals(state.surface(server,-2,3,index)),"Single-surface resolver matches full column: "+index);
        var restored=TemplateState.load(state.save(new net.minecraft.nbt.CompoundTag(),server.registryAccess()),server.registryAccess());
        helper.assertTrue(Arrays.equals(faces,restored.column(server,-2,3)),"Bindings and definitions survive world save");
        state.bind(lower.panel(),""); helper.assertTrue(!state.column(server,-2,3)[lower.index()].equals("test/stone#ceiling"),"Explicit reset follows default");
        helper.succeed();
    }
    @GameTest(template="empty",templateNamespace=Elsebase.ID)
    public static void offlineRegionDefaults(GameTestHelper helper) {
        var server=helper.getLevel().getServer(); var world=WorldState.get(server); var id=UUID.randomUUID(); var home=world.home(server,id); var state=new TemplateState();
        int x=home.slot().x()>>4,z=home.slot().z()>>4;
        try {
            state.defaults.put(id,TemplateState.builtin("arcane_archive"));
            helper.assertTrue(id.equals(state.owner(server,x,z)),"Allocated region resolves without an online player");
            helper.assertTrue(Arrays.stream(state.column(server,x,z)).allMatch(s -> s.contains("arcane_archive")),"Offline owner's default applies");
            state.bind(new Panel(x,z,Direction.NORTH,64),"builtin/deepstone_halls");
            state.defaults.put(id,TemplateState.builtin("service_layer")); state.changed();
            var column=state.column(server,x,z);
            helper.assertTrue(column[8*6+Direction.NORTH.ordinal()].equals("builtin/deepstone_halls#wall") && column[8*6+Direction.SOUTH.ordinal()].contains("service_layer"),"Default changes update inherited faces only");
            var saved=TemplateState.load(state.save(new net.minecraft.nbt.CompoundTag(),server.registryAccess()),server.registryAccess());
            helper.assertTrue(Arrays.equals(column,saved.column(server,x,z)),"Offline defaults survive saving");
            saved.defaults.remove(id); helper.assertTrue(!saved.column(server,x,z)[0].contains("service_layer"),"Removed profile falls back without chunk generation");
            helper.assertTrue(state.owner(server,x+world.allocationSpacing()/16,z)==null || !id.equals(state.owner(server,x+world.allocationSpacing()/16,z)),"Adjacent allocation region does not inherit this owner");
        } finally { world.homes.remove(id); world.setDirty(); }
        helper.succeed();
    }
    @GameTest(template="empty",templateNamespace=Elsebase.ID)
    public static void templateMaterialAndScannerPolicy(GameTestHelper helper) {
        helper.assertTrue(Materials.problem(Blocks.STONE.defaultBlockState())==null,"Stone allowed");
        helper.assertTrue(Materials.problem(Blocks.EMERALD_BLOCK.defaultBlockState()).equals("Material is blocked by this server"),"Server datapack can blacklist an otherwise valid material");
        for(var block:List.of(Blocks.OAK_LOG,Blocks.STRIPPED_BIRCH_LOG,Blocks.GILDED_BLACKSTONE,Blocks.COPPER_BLOCK,Blocks.SEA_LANTERN,Blocks.REDSTONE_LAMP)) helper.assertTrue(Materials.problem(block.defaultBlockState())==null,"Ordinary model accepted without registration: "+block);
        helper.assertTrue(Materials.problem(Content.structure(dev.elsebase.world.RoomLayout.Material.WALL))==null,"Mod full cube needs no material tag membership");
        for(var block:List.of(Blocks.CHEST,Blocks.GLASS,Blocks.WATER,Blocks.OAK_STAIRS)) helper.assertTrue(Materials.problem(block.defaultBlockState())!=null,"Special material rejected: "+block);
        helper.assertTrue(new Panel(0,0,Direction.NORTH,0).positions(true).size()==98,"Bounded wall scan");
        var level=helper.getLevel(); var pos=helper.absolutePos(new BlockPos(1,1,1)); var structural=Content.structure(dev.elsebase.world.RoomLayout.Material.WALL);
        helper.assertTrue(net.minecraft.world.level.block.Block.getDrops(structural,level,pos,null).isEmpty(),"Structure has no resource drops");
        for(var entry:new TemplateState().entries.values()) { entry.theme().validateMaterials(); helper.assertTrue(entry.theme().wall().width()==14 && new HashSet<>(entry.theme().wall().cells()).size()>1,"Built-ins contain multi-block wall patterns"); }
        helper.succeed();
    }

    @GameTest(template="empty",templateNamespace=Elsebase.ID)
    public static void playerWallsAndDirectionalMaterials(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var level=player.server.getLevel(Elsebase.DIMENSION); level.getChunk(110,110);
        var feet=new net.minecraft.world.phys.Vec3(1768.5,65,1768.5); var eye=feet.add(0,1.6,0);
        try {
            for(var side:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
                var panel=new Panel(110,110,side,64); var neighbor=new Panel(110+side.getStepX(),110+side.getStepZ(),side.getOpposite(),64);
                level.getChunk(neighbor.cellX(),neighbor.cellZ());
                var original=Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,side.getAxis());
                for(var pos:panel.positions(true)) level.setBlockAndUpdate(pos,original);
                for(var pos:neighbor.positions(true)) level.setBlockAndUpdate(pos,Content.structure(dev.elsebase.world.RoomLayout.Material.WALL));
                var look=new net.minecraft.world.phys.Vec3(side.getStepX(),0,side.getStepZ());
                helper.assertTrue(panel.equals(TemplateScanner.target(level,feet,eye,look)),"Scanner must stop at player-built wall before neighboring structure: "+side);
                player.teleportTo(level,feet.x,feet.y,feet.z,0,0);
                var captured=TemplateScanner.capture(player,panel,BuiltinThemes.create("deepstone_halls","Deepstone Halls").wall());
                helper.assertTrue(captured.at(2,3).properties().get("axis").equals("z"),"Scanned log axis is relative to wall");
                for(var target:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) helper.assertTrue(MaterialOrientation.toWorld(Materials.resolve(captured.at(2,3)),target).getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)==target.getAxis(),"Log end grain remains visible on every destination wall");
                for(var pos:panel.positions(true)) level.setBlockAndUpdate(pos,Blocks.GLASS.defaultBlockState());
                helper.assertTrue(panel.equals(TemplateScanner.target(level,feet,eye,look)),"Invalid ordinary blocks remain targeted for red highlight");
                for(var pos:panel.positions(true)) level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                helper.assertTrue(neighbor.equals(TemplateScanner.target(level,feet,eye,look)),"Empty wall exposes only the adjacent wall half");
            }
        } finally { player.connection.disconnect(net.minecraft.network.chat.Component.literal("GameTest completed")); }
        helper.succeed();
    }

    @GameTest(template="empty",templateNamespace=Elsebase.ID)
    public static void builtinHorizontalSymmetry(GameTestHelper helper) {
        for(String id:TemplateState.THEMES) {
            var theme=BuiltinThemes.create(id,id);
            for(var pattern:List.of(theme.floor(),theme.ceiling())) for(int v=0;v<16;v++) for(int u=0;u<16;u++) helper.assertTrue(Materials.resolve(pattern.at(u,v)).rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).equals(Materials.resolve(pattern.at(15-v,u))),"Built-in horizontal pattern and directed materials are invariant under quarter turns: "+id);
        }
        var deepstone=BuiltinThemes.create("deepstone_halls","Deepstone Halls");
        helper.assertTrue(deepstone.wall().at(1,2).block().equals("minecraft:deepslate_bricks") && deepstone.wall().at(0,2).block().equals("minecraft:tuff_bricks"),"Approved masonry fields and framing");
        helper.succeed();
    }
}
