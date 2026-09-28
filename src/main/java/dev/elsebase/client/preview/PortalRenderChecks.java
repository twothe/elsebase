package dev.elsebase.client.preview;

import dev.elsebase.*;
import dev.elsebase.portal.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;
import java.util.*;

/** Opt-in end-to-end client regression: fresh fixture, live pixels, block updates, transfer screens and cleanup. */
@EventBusSubscriber(modid=Elsebase.ID,value=Dist.CLIENT)
public final class PortalRenderChecks {
    private static int stage,ticks,vanillaScreens;
    private static Endpoint outside,inside;
    private static List<UUID> companions=List.of();
    private static long started;
    private static int brokenModelCalls;
    private static boolean shaderFallback;
    private static double brightImage,renderP95;
    private static long beforeReload;
    private static final net.neoforged.neoforge.client.model.data.ModelProperty<Boolean> BROKEN=new net.neoforged.neoforge.client.model.data.ModelProperty<>();
    private PortalRenderChecks() {}
    @SubscribeEvent public static void faultyModel(ModelEvent.ModifyBakingResult event) {
        if(!Boolean.getBoolean("elsebase.verifyPortals")) return;
        event.getModels().replaceAll((key,original) -> {
            if(!key.id().equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("emerald_block"))) return original;
            return new net.neoforged.neoforge.client.model.BakedModelWrapper<>(original) {
                @Override public net.neoforged.neoforge.client.model.data.ModelData getModelData(BlockAndTintGetter level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state,net.neoforged.neoforge.client.model.data.ModelData data) {
                    return level instanceof PreviewScene?data.derive().with(BROKEN,true).build():super.getModelData(level,pos,state,data);
                }
                @Override public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state,Direction side,net.minecraft.util.RandomSource random,net.neoforged.neoforge.client.model.data.ModelData data,net.minecraft.client.renderer.RenderType type) {
                    if(Boolean.TRUE.equals(data.get(BROKEN))) { brokenModelCalls++; throw new IllegalStateException("Intentional preview-only faulty model fixture"); }
                    return super.getQuads(state,side,random,data,type);
                }
            };
        });
    }
    @SubscribeEvent public static void screen(ScreenEvent.Render.Pre event) {
        if(Boolean.getBoolean("elsebase.verifyPortals") && stage>=3 && stage<=4 && event.getScreen() instanceof ReceivingLevelScreen) vanillaScreens++;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("elsebase.verifyPortals") || stage==7) return;
        var mc=Minecraft.getInstance();
        if(mc.mouseHandler.isMouseGrabbed()
                || org.lwjgl.glfw.GLFW.glfwGetInputMode(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_CURSOR)!=org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL)
            throw new IllegalStateException("Automated client captured the desktop cursor at stage "+stage);
        if(started==0) { started=System.currentTimeMillis(); result(mc,"running"); }
        if(System.currentTimeMillis()-started>180_000) throw new IllegalStateException("Portal client regression timed out at stage "+stage);
        if(stage==0 && mc.getOverlay()==null) {
            if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
            if(!(mc.screen instanceof TitleScreen)) return;
            stage=1; PreviewSettings.QUALITY.set(PreviewSettings.Quality.BALANCED); PreviewSettings.TRANSITION.set(true);
            shaderFallback=Boolean.getBoolean("elsebase.expectShaderFallback");
            require(ShaderCompatibility.activeShaders()==shaderFallback,"Expected shader compatibility mode");
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.valueOf(System.getProperty("elsebase.portalGraphics","fancy").toUpperCase(java.util.Locale.ROOT)));
            projectionChecks();
            mc.createWorldOpenFlows().createFreshLevel("elsebase-live-check-"+System.currentTimeMillis(),
                    new LevelSettings("Elsebase live regression",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42,false,false),registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        } else if(stage==1 && mc.player!=null && mc.getSingleplayerServer()!=null && mc.screen==null) {
            mc.mouseHandler.grabMouse();
            require(!mc.mouseHandler.isMouseGrabbed(),"Explicit mouse capture is suppressed in automated clients");
            stage=2; ticks=0; var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
            server.execute(() -> {
                var player=server.getPlayerList().getPlayer(id); var inner=server.getLevel(Elsebase.DIMENSION); var outer=server.overworld();
                outside=new Endpoint(Level.OVERWORLD,new BlockPos(8,-60,8),Direction.SOUTH);
                inside=new Endpoint(Elsebase.DIMENSION,new BlockPos(136,65,136),Direction.SOUTH);
                var templates=dev.elsebase.template.TemplateState.get(server);
                templates.entries.put("fixture/preview_floor",new dev.elsebase.template.TemplateState.Entry("fixture/preview_floor","",dev.elsebase.client.TemplateChecks.solidTheme("Portal floor fixture",Blocks.YELLOW_CONCRETE)));
                templates.bind(new dev.elsebase.structure.StructuralEditor.Panel(8,8,Direction.DOWN,64),"fixture/preview_floor");
                for(int x=7;x<=9;x++) for(int z=7;z<=9;z++) inner.getChunk(x,z);
                for(var endpoint : List.of(outside,inside)) {
                    var level=server.getLevel(endpoint.dimension()); level.getChunkAt(endpoint.position());
                    for(var p : endpoint.blocks()) level.setBlockAndUpdate(p,PortalBlock.stateAt(endpoint,p));
                    level.setBlockAndUpdate(endpoint.position().below(),Blocks.STONE.defaultBlockState());
                }
                for(int x=130;x<=142;x++) for(int y=65;y<=70;y++) inner.setBlockAndUpdate(new BlockPos(x,y,141),x<136?Blocks.RED_CONCRETE.defaultBlockState():Blocks.BLUE_CONCRETE.defaultBlockState());
                inner.setBlockAndUpdate(new BlockPos(138,66,141),Blocks.EMERALD_BLOCK.defaultBlockState());
                var tank=new BlockPos(140,66,139);
                for(Direction side : Direction.values()) inner.setBlockAndUpdate(tank.relative(side),Blocks.GLASS.defaultBlockState());
                inner.setBlockAndUpdate(tank,Blocks.WATER.defaultBlockState());
                WorldState.get(server).put(new PortalPair(UUID.randomUUID(),id,true,inside,outside));
                player.teleportTo(outer,8.5,-60,12.5,180,0);
                var cow=net.minecraft.world.entity.EntityType.COW.create(outer);
                var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(outer);
                cow.moveTo(7.5,-60,11.5,0,0); sheep.moveTo(9.5,-60,11.5,0,0);
                outer.addFreshEntity(cow); outer.addFreshEntity(sheep);
                cow.setLeashedTo(player,true); sheep.setLeashedTo(player,true);
                companions=List.of(cow.getUUID(),sheep.getUUID());
            });
        } else if(stage==2 && ++ticks>100 && shaderFallback) {
            require(PreviewClient.scene==null && !PreviewClient.renderer.ready,"Active shader pack keeps static fallback without scene streaming");
            capture(mc,"elsebase-shader-fallback.png"); startCrossing(mc);
        } else if(stage==2 && ticks>100 && PreviewClient.scene!=null && PreviewClient.renderer.renderedFrames>20 && PreviewClient.renderer.ready) {
            require(PreviewClient.scene.sections.size()>=3,"Destination section streaming");
            var floorPos=new BlockPos(135,64,136); var floorState=PreviewClient.scene.getBlockState(floorPos); var floorModel=mc.getBlockRenderer().getBlockModel(floorState);
            var floorData=floorModel.getModelData(PreviewClient.scene,floorPos,floorState,net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
            require(floorModel.getQuads(floorState,Direction.UP,net.minecraft.util.RandomSource.create(0),floorData,net.minecraft.client.renderer.RenderType.solid()).stream().anyMatch(q -> q.getSprite().contents().name().getPath().endsWith("yellow_concrete")),"Portal snapshot uses authoritative custom floor template");
            capture(mc,"elsebase-live-portal.png");
            require(visibleDestinationColors(mc),"Red/blue destination pixels appear on the actual doorway surface");
            // Check actual offscreen pixels rather than treating a render call as proof of an image.
            require(PreviewClient.renderer.hasColorVariation(),"Destination framebuffer contains textured geometry");
            require(brokenModelCalls==1 && SceneMesh.QUARANTINE.contains(net.minecraft.resources.ResourceLocation.withDefaultNamespace("emerald_block")),"Faulty baked model quarantined after one attempt");
            require(SceneMesh.QUARANTINE.size()==1,"Ordinary block, glass and fluid models remain renderable");
            stage=3; ticks=0;
            var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
            server.execute(() -> {
                server.getLevel(Elsebase.DIMENSION).setBlockAndUpdate(new BlockPos(136,66,141),Blocks.GOLD_BLOCK.defaultBlockState());
                var player=server.getPlayerList().getPlayer(id); player.teleportTo(server.overworld(),8.8,-60,12.5,180,0);
            });
        } else if(stage==3 && ++ticks>60 && PreviewClient.scene!=null && PreviewClient.scene.getBlockState(new BlockPos(136,66,141)).is(Blocks.GOLD_BLOCK)) {
            capture(mc,"elsebase-live-parallax.png"); brightImage=PreviewClient.renderer.averageBrightness();
            renderP95=PreviewClient.renderer.renderP95Millis(); stage=8; ticks=0;
            mc.getSingleplayerServer().execute(() -> Settings.DARKNESS.set(true));
        } else if(stage==8 && ++ticks>60 && PreviewClient.scene!=null && !PreviewClient.scene.description.bright() && PreviewClient.scene.dirty.isEmpty()) {
            require(PreviewClient.renderer.averageBrightness()<brightImage*.5,"Target darkness applies while source world stays bright");
            capture(mc,"elsebase-live-darkness.png"); stage=9; ticks=0;
            mc.getSingleplayerServer().execute(() -> Settings.DARKNESS.set(false));
        } else if(stage==9 && ++ticks>40 && PreviewClient.scene!=null && PreviewClient.scene.description.bright() && PreviewClient.scene.dirty.isEmpty()) {
            beforeReload=PreviewClient.scene.description.generation(); stage=10; ticks=0;
            // Simulate eviction of historical appearances: a new scene must not rely on old delivery acknowledgments.
            dev.elsebase.client.TemplateModels.COLUMNS.clear(); mc.reloadResourcePacks();
        } else if(stage==10 && ++ticks>60 && mc.getOverlay()==null && PreviewClient.scene!=null && PreviewClient.scene.description.generation()!=beforeReload && PreviewClient.renderer.ready) {
            require(PreviewClient.renderer.hasColorVariation(),"Resource reload reacquires snapshots and rebuilds GPU resources");
            require(brokenModelCalls==2,"Model quarantine resets on resource reload and retries exactly once");
            var appearance=dev.elsebase.client.TemplateModels.pattern(new BlockPos(135,64,136),Direction.UP);
            require(appearance!=null && appearance.at(7,8).block().equals("minecraft:yellow_concrete"),"Restarted preview reacquires evicted column appearances"); startCrossing(mc);
        } else if(stage==4 && mc.level!=null && mc.level.dimension().equals(Elsebase.DIMENSION) && mc.screen==null && ++ticks>120) {
            checkCompanions(mc);
            require(PortalTransition.replacedScreens==2,"Both vanilla transition screens replaced");
            require(vanillaScreens==0,"No vanilla receiving screen rendered during portal travel");
            require(PortalTransition.capturedArrivals==(shaderFallback?0:1),"Arrival presentation follows preview/shader availability");
            capture(mc,"elsebase-live-arrival.png"); stage=5; ticks=0;
            var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
            server.execute(() -> server.getPlayerList().getPlayer(id).teleportTo(server.overworld(),8.5,-60,12.5,180,0));
        } else if(stage==5 && mc.level!=null && mc.level.dimension().equals(Level.OVERWORLD) && mc.screen==null && ++ticks>30) {
            require(PortalTransition.replacedScreens==2,"Unmarked teleport retains vanilla behavior");
            PreviewSettings.QUALITY.set(PreviewSettings.Quality.OFF); stage=6; ticks=0;
        } else if(stage==6 && ++ticks>40) {
            require(PreviewClient.scene==null,"Preview disable releases cached scene"); capture(mc,"elsebase-live-fallback.png");
            result(mc,"passed"); Elsebase.LOGGER.info("PASS: live portal framebuffer, destination updates, two-screen transition, selective scope and fallback cleanup");
            stage=7; mc.stop();
        }
    }
    /** Check real server retention and client link delivery after ordinary AI and network ticks. */
    private static void checkCompanions(Minecraft mc) {
        var server=mc.getSingleplayerServer(); var playerId=mc.player.getUUID();
        var entityIds=server.submit(() -> {
            var player=server.getPlayerList().getPlayer(playerId);
            var ids=new ArrayList<Integer>();
            for(var uuid:companions) {
                var entity=player.serverLevel().getEntity(uuid);
                require(entity instanceof net.minecraft.world.entity.Leashable leash && leash.getLeashHolder()==player,
                        "Server retains vanilla animal leash after arrival ticks: "+uuid);
                ids.add(entity.getId());
            }
            return ids;
        }).join();
        for(int id:entityIds) require(mc.level.getEntity(id) instanceof net.minecraft.world.entity.Leashable leash && leash.getLeashHolder()==mc.player,
                "Client receives retained animal leash: "+id);
        server.submit(() -> {
            for(var uuid:companions) {
                var entity=server.getLevel(Elsebase.DIMENSION).getEntity(uuid);
                ((net.minecraft.world.entity.Leashable)entity).dropLeash(true,false);
                entity.discard();
            }
        }).join();
    }

    private static void startCrossing(Minecraft mc) {
        stage=4; ticks=0; vanillaScreens=0; PortalTransition.replacedScreens=0;
        var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
        server.execute(() -> {
            var player=server.getPlayerList().getPlayer(id); player.teleportTo(server.overworld(),8.5,-60,8.5,180,0);
            Portals.cross(player,outside.position());
        });
    }
    private static void projectionChecks() {
        for(Direction facing : Direction.Plane.HORIZONTAL) for(int side : new int[]{-1,1}) {
            var a=new Endpoint(Level.OVERWORLD,new BlockPos(-131000,65,131000),facing);
            var b=new Endpoint(Elsebase.DIMENSION,new BlockPos(131000,65,-131000),Direction.WEST);
            var eye=a.center().add(net.minecraft.world.phys.Vec3.atLowerCornerOf(facing.getNormal()).scale(side*4)).add(0,1.62,0);
            var projection=PortalProjection.create(eye,a,b); require(projection!=null,"Cardinal/reverse-side projection exists");
            var normal=net.minecraft.world.phys.Vec3.atLowerCornerOf(b.facing().getNormal()).scale(side);
            var right=normal.cross(new net.minecraft.world.phys.Vec3(0,1,0));
            var corner=new net.minecraft.world.phys.Vec3(.5,.03125,.5).subtract(right.scale(.4375));
            var clip=new org.joml.Vector4f((float)corner.x,(float)corner.y,(float)corner.z,1).mul(projection.view()).mul(projection.projection());
            require(Math.abs(clip.x/clip.w+1)<.001 && Math.abs(clip.y/clip.w+1)<.001,"Off-axis window maps lower-left corner exactly");
        }
    }
    private static void require(boolean value,String description) { if(!value) throw new IllegalStateException("Portal regression: "+description); Elsebase.LOGGER.info("PASS: {}",description); }
    private static boolean visibleDestinationColors(Minecraft mc) {
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int red=0,blue=0;
            for(int y=image.getHeight()/3;y<image.getHeight()*4/5;y++) for(int x=image.getWidth()*2/5;x<image.getWidth()*3/5;x++) {
                int pixel=image.getPixelRGBA(x,y),r=pixel&255,g=pixel>>8&255,b=pixel>>16&255;
                if(r>40 && r>g*2 && r>b*2) red++;
                if(b>40 && b>r*2 && b>g*2) blue++;
            }
            return red>50 && blue>50;
        }
    }
    private static void capture(Minecraft mc,String filename) {
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var dir=mc.gameDirectory.toPath().resolve("screenshots"); Files.createDirectories(dir); image.writeToFile(dir.resolve(filename));
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static void result(Minecraft mc,String status) {
        try { Files.writeString(mc.gameDirectory.toPath().resolve("portal-check-result.json"),"{\"status\":\""+status+"\",\"started\":"+started+",\"replacedScreens\":"+PortalTransition.replacedScreens+",\"renderSubmissionP95Ms\":"+renderP95+"}"); }
        catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
