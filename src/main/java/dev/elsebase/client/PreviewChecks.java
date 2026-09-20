package dev.elsebase.client;

import dev.elsebase.*;
import dev.elsebase.structure.StructuralEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in actual-client preview fixture. Creates a unique disposable world, never opens a user's save. */
@EventBusSubscriber(modid = Elsebase.ID, value = Dist.CLIENT)
public final class PreviewChecks {
    private static int stage, ticks;
    private PreviewChecks() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("elsebase.verifyPreview") || stage==6) return;
        var mc = Minecraft.getInstance();
        if (stage==0 && mc.getOverlay()==null) {
            if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
            if (!(mc.screen instanceof TitleScreen)) return;
            stage=1;
            mc.createWorldOpenFlows().createFreshLevel("elsebase-preview-"+System.currentTimeMillis(),
                    new LevelSettings("Elsebase preview check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L,false,false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        } else if (stage==1 && mc.player!=null && mc.getSingleplayerServer()!=null) {
            stage=2; ticks=0;
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                var level = server.getLevel(Elsebase.DIMENSION);
                level.getChunk(8,8);
                var wall = new StructuralEditor.Panel(8,8,Direction.WEST,64);
                for (var pos : wall.positions(false)) level.removeBlock(pos,false);
                for (int y : new int[]{64,72}) for (int z=128;z<144;z++) level.removeBlock(new net.minecraft.core.BlockPos(128,y,z),false);
                player.teleportTo(level,136.5,65,136.5,90,0);
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Content.CREATION_TOOL.get()));
                player.inventoryMenu.broadcastChanges();
            });
        } else if (stage==2 && mc.player!=null && mc.level.dimension().equals(Elsebase.DIMENSION) && mc.screen==null && ++ticks>100) {
            capture(mc,"elsebase-preview-wall.png");
            mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            stage=3; ticks=0;
        } else if (stage==3 && ++ticks>40) {
            var wall = new StructuralEditor.Panel(8,8,Direction.WEST,64);
            if (!wall.positions(false).stream().allMatch(pos -> mc.level.getBlockState(pos).is(Content.WALL.get())))
                throw new IllegalStateException("Actual client air-use did not restore previewed wall");
            Elsebase.LOGGER.info("PASS: actual client air-use restores the previewed wall without border blocks");
            mc.player.setXRot(60);
            stage=4; ticks=0;
        } else if (stage==4 && ++ticks>40) {
            capture(mc,"elsebase-preview-floor.png");
            stage=5; ticks=0;
            var server=mc.getSingleplayerServer();
            var uuid=mc.player.getUUID();
            server.execute(() -> {
                var player=server.getPlayerList().getPlayer(uuid);
                var level=server.getLevel(Elsebase.DIMENSION);
                var endpoint=new dev.elsebase.portal.Endpoint(Elsebase.DIMENSION,new net.minecraft.core.BlockPos(132,65,136),Direction.EAST);
                for (var pos : endpoint.blocks()) level.setBlockAndUpdate(pos,dev.elsebase.portal.PortalBlock.stateAt(endpoint,pos));
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
                player.teleportTo(level,136.5,65,136.5,90,0);
            });
        } else if (stage==5 && ++ticks>60) {
            capture(mc,"elsebase-portal-surface.png");
            stage=6;
        }
    }
    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message -> Elsebase.LOGGER.info("Preview capture: {}",message.getString()));
    }
}
