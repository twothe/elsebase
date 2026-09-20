package dev.elsebase.client;

import dev.elsebase.*;
import dev.elsebase.template.*;
import dev.elsebase.structure.StructuralEditor.Panel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in isolated actual-client checks: textured faces, live overwrite, free cursor and template menu. */
@EventBusSubscriber(modid=Elsebase.ID,value=Dist.CLIENT)
public final class TemplateChecks {
    private static int stage,ticks,gallery;
    private static long started;
    private static volatile boolean prepared;
    public static Pattern solid(String name,net.minecraft.world.level.block.Block block) { return new Pattern(name,"Fixture",1,1,List.of(Materials.describe(block.defaultBlockState())),List.of(0)); }
    public static Theme solidTheme(String name,net.minecraft.world.level.block.Block block) { var pattern=solid(name,block); return new Theme(name,"Fixture",pattern,pattern,pattern); }
    /** Assert the dimension policy before chunks may compile, not only after the first client tick. */
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void levelCreated(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        if(Boolean.getBoolean("elsebase.verifyTemplates") && event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level)
            require(TemplateModels.inBackdoor==level.dimension().equals(Elsebase.DIMENSION),"Appearance dimension policy is initialized at level creation");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("elsebase.verifyTemplates") || stage==6) return;
        var mc=Minecraft.getInstance(); if(started==0) started=System.currentTimeMillis();
        if(System.currentTimeMillis()-started>180000) throw new IllegalStateException("Template client timeout stage "+stage);
        require(!mc.mouseHandler.isMouseGrabbed(),"Test must not grab cursor");
        for(var source:net.minecraft.sounds.SoundSource.values()) require(mc.options.getSoundSourceVolume(source)==0.0F,"Automated client audio must be muted");
        if(stage==0 && mc.getOverlay()==null) {
            if(mc.screen instanceof AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
            if(!(mc.screen instanceof TitleScreen)) return;
            require(dev.elsebase.client.preview.ShaderCompatibility.activeShaders()==Boolean.getBoolean("elsebase.templateShaderExpected"),"Requested shader fixture is active");
            stage=1; mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.createWorldOpenFlows().createFreshLevel("elsebase-template-check-"+started,new LevelSettings("Template regression",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(47,false,false),registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        } else if(stage==1 && mc.player!=null && mc.screen==null && mc.getSingleplayerServer()!=null) {
            stage=2; ticks=0; var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
            server.execute(() -> {
                var player=server.getPlayerList().getPlayer(id); var level=server.getLevel(Elsebase.DIMENSION); level.getChunk(0,0);
                var state=TemplateState.get(server);
                state.entries.put("fixture/wall",new TemplateState.Entry("fixture/wall","",solidTheme("Red",Blocks.RED_CONCRETE)));
                state.entries.put("fixture/floor",new TemplateState.Entry("fixture/floor","",solidTheme("Blue",Blocks.BLUE_CONCRETE)));
                state.entries.put("fixture/ceiling",new TemplateState.Entry("fixture/ceiling","",solidTheme("Gold",Blocks.GOLD_BLOCK)));
                for(var side:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}) {
                    var panel=new Panel(0,0,side,64); for(var pos:panel.positions(true)) level.setBlockAndUpdate(pos,Content.structure(dev.elsebase.world.RoomLayout.Material.WALL)); state.bind(panel,"fixture/wall");
                }
                state.bind(new Panel(0,0,Direction.DOWN,64),"fixture/floor"); state.bind(new Panel(0,0,Direction.UP,56),"fixture/ceiling");
                player.teleportTo(level,8.5,65,8.5,180,0); player.setItemInHand(InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY); prepared=true;
            });
        } else if(stage==2 && prepared && mc.screen==null && ++ticks>100 && TemplateModels.PATTERNS.containsKey("fixture/wall#wall") && TemplateModels.COLUMNS.containsKey(0L)) {
            checkDirectionalModels(mc);
            require(TemplateModels.pattern(new BlockPos(8,64,8),Direction.UP).name().equals("Blue"),"Top slab is blue floor");
            require(TemplateModels.pattern(new BlockPos(8,64,8),Direction.DOWN).name().equals("Gold"),"Bottom slab is independent gold ceiling");
            capture(mc,"template-red-wall.png");
            var block=mc.level.getBlockState(new BlockPos(8,65,0));
            Elsebase.LOGGER.info("Template fixture: state={}, model={}, dimension={}, enabled={}, redPixels={}",block,mc.getBlockRenderer().getBlockModel(block).getClass().getName(),mc.level.dimension(),TemplateModels.inBackdoor,colored(mc,0));
            require(colored(mc,0)>300,"Actual red wall pixels");
            stage=3; ticks=0; var server=mc.getSingleplayerServer(); server.execute(() -> { var state=TemplateState.get(server); state.entries.put("fixture/wall",new TemplateState.Entry("fixture/wall","",solidTheme("Green",Blocks.LIME_CONCRETE))); state.changed(); });
        } else if(stage==3 && ++ticks>100 && TemplateModels.PATTERNS.get("fixture/wall#wall").name().equals("Green")) {
            capture(mc,"template-updated-wall.png"); require(colored(mc,1)>300,"Actual green wall pixels after live overwrite");
            stage=16; ticks=0; var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
            server.execute(() -> server.getPlayerList().getPlayer(id).teleportTo(server.overworld(),8.5,-60,8.5,180,0));
        } else if(stage==16 && mc.level.dimension().equals(Level.OVERWORLD) && mc.screen==null && ++ticks>40) {
            require(TemplateModels.COLUMNS.containsKey(0L),"Appearance cache survives leaving the dimension");
            stage=17; ticks=0; var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
            server.execute(() -> server.getPlayerList().getPlayer(id).teleportTo(server.getLevel(Elsebase.DIMENSION),8.5,65,8.5,180,0));
        } else if(stage==17 && mc.level.dimension().equals(Elsebase.DIMENSION) && mc.screen==null && ++ticks>100) {
            require(colored(mc,1)>300,"Actual styled wall pixels survive cached-column reentry"); capture(mc,"template-reentry.png");
            stage=4; ticks=0; var server=mc.getSingleplayerServer(); var id=mc.player.getUUID(); server.execute(() -> { var player=server.getPlayerList().getPlayer(id); player.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Content.CREATION_TOOL.get())); TemplateServer.open(player); });
        } else if(stage==4 && mc.screen instanceof TemplateScreen && ++ticks>30) {
            require(TemplateClient.catalog.getAsJsonArray("rows").size()>=7,"Built-in themes and authoritative catalog available");
            require(buttons(mc).containsAll(List.of("Select","Cancel")) && !buttons(mc).contains("Use as default") && !buttons(mc).contains("Import...") && !buttons(mc).contains("Wall") && !buttons(mc).contains("Rotate"),"Construction has only selection actions, no role or library actions"); capture(mc,"template-menu.png");
            var export=new TemplateSaveScreen(mc.screen,solidTheme("Original name",Blocks.STONE)); mc.setScreen(export);
            var name=(net.minecraft.client.gui.components.EditBox)export.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();
            name.setValue("Renamed local copy"); mc.setScreen(null); mc.setScreen(export);
            require(export.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox).map(child -> ((net.minecraft.client.gui.components.EditBox)child).getValue()).anyMatch("Renamed local copy"::equals),"Local export rename survives confirmation-screen reinitialization");
            mc.setScreen(null); stage=7; ticks=0;
            var server=mc.getSingleplayerServer(); var id=mc.player.getUUID(); server.execute(() -> { var player=server.getPlayerList().getPlayer(id); player.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Content.PAINT_TOOL.get())); TemplateServer.paint(player); });
        } else if(stage==7 && mc.screen instanceof TemplateScreen && ++ticks>30) {
            require(TemplateClient.catalog.get("selected").getAsString().isEmpty(),"Unselected paint use opens menu without implicit theme");
            require(buttons(mc).containsAll(List.of("Select","Use as default","Cancel")) && !buttons(mc).contains("Import..."),"Paint has select/default/cancel actions");
            mc.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals("Arcane Archive")).map(child -> (net.minecraft.client.gui.components.Button)child).findFirst().orElseThrow().onPress(); stage=8; ticks=0;
        } else if(stage==8 && mc.screen instanceof TemplateScreen && ++ticks>30) {
            capture(mc,"theme-paint-menu.png"); press(mc,"Deepstone Halls"); stage=12; ticks=0;
        } else if(stage==12 && mc.screen instanceof TemplateScreen && ++ticks>30) {
            capture(mc,"deepstone-room.png"); press(mc,"Show ceiling"); stage=13; ticks=0;
        } else if(stage==13 && mc.screen instanceof TemplateScreen && ++ticks>30) {
            capture(mc,"deepstone-ceiling.png"); mc.setScreen(null); stage=9; ticks=0;
            var server=mc.getSingleplayerServer(); var id=mc.player.getUUID(); server.execute(() -> { var player=server.getPlayerList().getPlayer(id); player.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Content.SCANNER.get())); TemplateScanner.select(player,TemplateState.builtin("arcane_archive"),true); TemplateScanner.scan(player); TemplateServer.open(player); });
        } else if(stage==9 && mc.screen instanceof TemplateScreen && ++ticks>30) {
            require(buttons(mc).containsAll(List.of("Import...","Export...","Save as my theme...","New Theme...")) && !TemplateClient.changes.isEmpty(),"Scanner exposes library and buffered save with dirty status"); capture(mc,"theme-scanner-menu.png");
            press(mc,"New Theme..."); require(mc.screen instanceof ConfirmScreen,"Dirty scan buffer requires confirmation before New Theme");
            press(mc,"No"); require(mc.screen instanceof TemplateScreen && !TemplateClient.changes.isEmpty(),"Cancel keeps unsaved scans");
            press(mc,"New Theme..."); press(mc,"Yes"); require(mc.screen instanceof NewThemeScreen,"New Theme opens naming dialog");
            var name=(net.minecraft.client.gui.components.EditBox)mc.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow(); name.setValue("Client new theme");
            press(mc,"Create"); stage=10; ticks=0;
        } else if(stage==10 && mc.screen instanceof TemplateScreen && ++ticks>30 && TemplateClient.draftBase.isEmpty()) {
            require(TemplateClient.draft!=null && TemplateClient.draft.name().equals("Client new theme"),"New named theme buffer received"); capture(mc,"theme-new-draft.png");
            press(mc,"Save as my theme..."); require(mc.screen instanceof TemplateUploadScreen,"New draft can be saved without scanning first"); press(mc,"Save"); stage=11; ticks=0;
        } else if(stage==11 && mc.screen instanceof TemplateScreen && ++ticks>30 && TemplateClient.draftBase.startsWith("player/")) {
            require(TemplateClient.changes.isEmpty(),"New theme save clears dirty state");
            String saved=TemplateClient.draftBase; var server=mc.getSingleplayerServer(); var owner=mc.player.getUUID();
            mc.setScreen(null); stage=14; ticks=0;
            server.execute(() -> {
                var player=server.getPlayerList().getPlayer(owner); player.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Content.PAINT_TOOL.get()));
                TemplateState.get(server).choices.computeIfAbsent(owner,key -> new EnumMap<>(ToolItem.Kind.class)).put(ToolItem.Kind.PAINT,saved); TemplateServer.open(player);
            });
        } else if(stage==14 && mc.screen instanceof TemplateScreen && ++ticks>30) {
            var actions=mc.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button).map(child -> (net.minecraft.client.gui.components.Button)child).toList();
            int selectY=actions.stream().filter(b -> b.getMessage().getString().equals("Select")).findFirst().orElseThrow().getY();
            require(actions.stream().filter(b -> List.of("Use as default","Delete...").contains(b.getMessage().getString())).allMatch(b -> b.getY()+20<selectY),"Secondary Paint actions are separated above Select/Cancel");
            capture(mc,"theme-delete-menu.png"); press(mc,"Delete..."); require(mc.screen instanceof ConfirmScreen,"Delete requires confirmation");
            press(mc,"No"); require(mc.screen instanceof TemplateScreen,"Cancel deletion retains selection");
            press(mc,"Delete..."); press(mc,"Yes"); stage=15; ticks=0;
        } else if(stage==15 && mc.screen instanceof TemplateScreen && ++ticks>30 && TemplateClient.catalog.get("selected").getAsString().isEmpty()) {
            require(TemplateClient.catalog.getAsJsonArray("rows").asList().stream().noneMatch(row -> row.getAsJsonObject().get("name").getAsString().equals("Client new theme")),"Deleted theme disappears from authoritative catalog");
            mc.setScreen(null); prepared=true; stage=5; ticks=0;
        } else if(stage==5 && prepared && ++ticks>10) {
            gallery=0; openGallery(mc); stage=20; ticks=0;
        } else if(stage==20 && mc.screen instanceof TemplateScreen && ++ticks>30) {
            String id=TemplateState.THEMES[gallery]; var theme=TemplateClient.THEMES.get(TemplateState.builtin(id));
            require(theme!=null,"Gallery receives authoritative theme");
            for(var pattern:List.of(theme.wall(),theme.floor(),theme.ceiling())) for(var material:pattern.palette()) require(TemplateModels.supported(material),"Built-in material renders: "+id+" "+material);
            capture(mc,"theme-"+id+"-room.png"); press(mc,"Show ceiling"); stage=21; ticks=0;
        } else if(stage==21 && mc.screen instanceof TemplateScreen && ++ticks>30) {
            capture(mc,"theme-"+TemplateState.THEMES[gallery]+"-ceiling.png");
            if(++gallery<TemplateState.THEMES.length) { openGallery(mc); stage=20; ticks=0; }
            else { mc.setScreen(null); stage=22; }
        } else if(stage==22) {
            try { Files.writeString(mc.gameDirectory.toPath().resolve("template-check-result.json"),"{\"status\":\"passed\",\"started\":"+started+"}"); }
            catch(java.io.IOException error) { throw new IllegalStateException(error); }
            Elsebase.LOGGER.info("PASS: template pixels, independent slab faces, live overwrite, catalog, room preview and free cursor"); stage=6; mc.stop();
        }
    }
    private static void openGallery(Minecraft mc) {
        var server=mc.getSingleplayerServer(); var owner=mc.player.getUUID(); String id=TemplateState.builtin(TemplateState.THEMES[gallery]);
        server.execute(() -> {
            var player=server.getPlayerList().getPlayer(owner); player.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Content.CREATION_TOOL.get()));
            TemplateState.get(server).choices.computeIfAbsent(owner,key -> new EnumMap<>(ToolItem.Kind.class)).put(ToolItem.Kind.CREATE,id); TemplateServer.open(player);
        });
    }
    /** Exercise real baked models through structural face assignment, including wall-relative log axes. */
    private static void checkDirectionalModels(Minecraft mc) {
        for(var block:List.of(Blocks.OAK_LOG,Blocks.GILDED_BLACKSTONE,Blocks.PRISMARINE,Blocks.SEA_LANTERN)) require(TemplateModels.supported(Materials.describe(block.defaultBlockState())),"Full-cube model supported: "+block);
        String key="fixture/orientation#wall"; var previous=TemplateModels.COLUMNS.get(0L); var faces=new String[96]; Arrays.fill(faces,key); TemplateModels.COLUMNS.put(0L,faces);
        try {
            for(var axis:List.of(Direction.Axis.Z,Direction.Axis.X)) {
                var material=Materials.describe(Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,axis));
                TemplateModels.PATTERNS.put(key,new Pattern("Log orientation","Test",1,1,List.of(material),List.of(0)));
                for(var wall:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
                    var pos=switch(wall) { case NORTH -> new BlockPos(8,66,0); case SOUTH -> new BlockPos(8,66,15); case EAST -> new BlockPos(15,66,8); default -> new BlockPos(0,66,8); };
                    var state=mc.level.getBlockState(pos); var model=mc.getBlockRenderer().getBlockModel(state);
                    var data=model.getModelData(mc.level,pos,state,net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
                    var quads=model.getQuads(state,wall.getOpposite(),net.minecraft.util.RandomSource.create(0),data,net.minecraft.client.renderer.RenderType.solid());
                    String texture=axis==Direction.Axis.Z?"oak_log_top":"oak_log";
                    require(!quads.isEmpty() && quads.stream().allMatch(q -> q.getSprite().contents().name().getPath().equals("block/"+texture)),"Correct log texture on "+wall+" for canonical "+axis);
                }
            }
        } finally { TemplateModels.COLUMNS.put(0L,previous); TemplateModels.PATTERNS.remove(key); }
    }
    private static void press(Minecraft mc,String label) { mc.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button button && button.active && button.getMessage().getString().equals(label)).map(child -> (net.minecraft.client.gui.components.Button)child).findFirst().orElseThrow(() -> new IllegalStateException("Missing enabled button: "+label)).onPress(); }
    private static List<String> buttons(Minecraft mc) { return mc.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button).map(child -> ((net.minecraft.client.gui.components.Button)child).getMessage().getString()).toList(); }
    private static int colored(Minecraft mc,int channel) {
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int count=0; for(int y=image.getHeight()/4;y<image.getHeight()*3/4;y++) for(int x=image.getWidth()/4;x<image.getWidth()*3/4;x++) { int pixel=image.getPixelRGBA(x,y),r=pixel&255,g=pixel>>8&255,b=pixel>>16&255; if(channel==0?r>g*2 && r>b*2 && r>45:g>r*1.3 && g>b*1.3 && g>45) count++; } return count;
        }
    }
    private static void capture(Minecraft mc,String name) { try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) { var path=mc.gameDirectory.toPath().resolve("screenshots"); Files.createDirectories(path); image.writeToFile(path.resolve(name)); } catch(java.io.IOException error) { throw new IllegalStateException(error); } }
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException("Template regression: "+message); }
    private TemplateChecks() {}
}
