package dev.elsebase;

import dev.elsebase.portal.*;
import dev.elsebase.structure.StructuralEditor;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server lifecycle and narrow policy hooks; no blanket explosion cancellation or fake-player ban. */
public final class ServerEvents {
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void attacked(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getAmount() > 0 && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            CombatLock.attacked(player, event.getSource());
    }
    @SubscribeEvent public void respawn(net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent event) {
        BackdoorStart.respawn(event);
    }
    @SubscribeEvent public void starting(ServerStartingEvent event) {
        new dev.elsebase.world.SlotAllocator(Settings.RADIUS.get(), Settings.SPACING.get());
        WorldState.get(event.getServer());
        dev.elsebase.template.TemplateServer.start(event.getServer());
    }
    @SubscribeEvent public void watched(net.neoforged.neoforge.event.level.ChunkWatchEvent.Sent event) {
        if(event.getLevel().dimension().equals(Elsebase.DIMENSION)) dev.elsebase.template.TemplateServer.watch(event.getPlayer(),event.getPos(),true);
    }
    @SubscribeEvent public void unwatched(net.neoforged.neoforge.event.level.ChunkWatchEvent.UnWatch event) {
        if(event.getLevel().dimension().equals(Elsebase.DIMENSION)) dev.elsebase.template.TemplateServer.watch(event.getPlayer(),event.getPos(),false);
    }
    @SubscribeEvent public void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
                || level.getServer().overworld() == null) return;
        var data = WorldState.get(level.getServer());
        java.util.Set<net.minecraft.core.BlockPos> markers = new java.util.HashSet<>();
        data.homes.values().forEach(home -> { if (home.anchor() != null) markers.add(home.anchor()); });
        var sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.is(Content.PORTAL.get()) || state.is(Content.ANCHOR.get()))) continue;
            int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                var block = section.getBlockState(x,y,z);
                var pos = new net.minecraft.core.BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z);
                boolean orphan = block.is(Content.PORTAL.get()) && data.at(level.dimension(),pos) == null
                        || block.is(Content.ANCHOR.get()) && (!level.dimension().equals(Elsebase.DIMENSION) || !markers.contains(pos));
                if (orphan) chunk.setBlockState(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
                else if (block.is(Content.PORTAL.get())) {
                    var pair = data.at(level.dimension(), pos);
                    var endpoint = level.dimension().equals(Elsebase.DIMENSION) ? pair.inner() : pair.external();
                    var canonical = PortalBlock.stateAt(endpoint, pos);
                    if (!block.equals(canonical)) chunk.setBlockState(pos, canonical, false);
                }
            }
        }
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        dev.elsebase.template.TemplateServer.tick(event.getServer());
        Portals.tick();
        InstantExpiry.tick(event.getServer());
        StructuralEditor.tick(event.getServer());
        dev.elsebase.preview.PreviewServer.tick(event.getServer());
        if (event.getServer().getTickCount() % 20 == 0) {
            MirrorLoading.tick(event.getServer()); Network.refreshLighting(event.getServer());
        }
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        dev.elsebase.template.TemplateServer.clear();
        Network.serverStopped(); Anchors.clear(); MirrorLoading.clear(); Portals.clear(); StructuralEditor.clear(); dev.elsebase.preview.PreviewServer.clear();
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) InstantExpiry.residence(player); Anchors.release(event.getEntity().getUUID()); Portals.logout(event.getEntity().getUUID()); dev.elsebase.template.TemplateServer.logout(event.getEntity().getUUID()); dev.elsebase.preview.PreviewServer.logout(event.getEntity().getUUID()); }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            Network.syncLighting(player);
            if (!BackdoorStart.login(player)) return;
            InstantExpiry.residence(player);
            Anchors.ensure(player); // Passive preparation must not clear construction or veto later arrival recovery.
        }
    }
    @SubscribeEvent public void entered(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) InstantExpiry.used(player);
        if (event.getTo().equals(Elsebase.DIMENSION) && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            Anchors.ensure(player);
    }
    @SubscribeEvent public void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> StructuralBlock.protectedStructure(event.getLevel(), event.getLevel().getBlockState(pos))
                || event.getLevel().getBlockState(pos).is(Content.PORTAL.get()) || event.getLevel().getBlockState(pos).is(Content.ANCHOR.get()));
    }
    @SubscribeEvent public void spawn(MobSpawnEvent.PositionCheck event) {
        if (event.getLevel().getLevel().dimension().equals(Elsebase.DIMENSION) && !Settings.NATURAL_SPAWNS.get()
                && (event.getSpawnType() == MobSpawnType.NATURAL || event.getSpawnType() == MobSpawnType.CHUNK_GENERATION))
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        dev.elsebase.template.TemplateServer.commands(event);
        event.getDispatcher().register(Commands.literal("elsebase")
                .then(Commands.literal("portal").executes(c -> { Portals.request(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("rescue").requires(source -> source.hasPermission(2)).executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    var level = player.server.overworld();
                    var spawn = level.getSharedSpawnPos();
                    player.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5, 0, 0);
                    c.getSource().sendSuccess(() -> Component.literal("Operator recovery to the overworld spawn."), true);
                    return 1;
                })));
    }
}
