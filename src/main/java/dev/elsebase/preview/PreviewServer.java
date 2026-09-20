package dev.elsebase.preview;

import dev.elsebase.*;
import dev.elsebase.portal.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

/** Fair bounded polling of already-loaded destination sections. Previews never load/tick extra chunks. */
public final class PreviewServer {
    public enum Budget { OFF, LOW, BALANCED, HIGH }
    private record Preference(int quality, int expires) {}
    private static final Map<UUID,Preference> PREFERENCES = new HashMap<>();
    private static final Map<UUID,Subscription> ACTIVE = new LinkedHashMap<>();
    private static long generation;
    private static int cursor;
    private static final class Subscription {
        PreviewProtocol.Scene scene;
        final List<BlockPos> origins = new ArrayList<>();
        final Map<BlockPos,PreviewProtocol.Section> previous = new HashMap<>();
        int next;
        Subscription(PreviewProtocol.Scene scene) {
            this.scene=scene;
            var center=BlockPos.containing(scene.target().center());
            for (int y=-1;y<=1;y++) for(int z=-1;z<=1;z++) for(int x=-1;x<=1;x++) {
                var p=new BlockPos(((center.getX()>>4)+x)<<4,((center.getY()>>4)+y)<<4,((center.getZ()>>4)+z)<<4);
                if (p.getY()>=scene.minY() && p.getY()<scene.minY()+scene.height()) origins.add(p);
            }
            origins.sort(Comparator.comparingDouble(p -> p.offset(8,8,8).distSqr(center)));
        }
    }
    private PreviewServer() {}
    public static void preference(ServerPlayer player, int quality, boolean restart) {
        PREFERENCES.put(player.getUUID(),new Preference(quality,player.server.getTickCount()+100));
        if (quality==0 || restart) ACTIVE.remove(player.getUUID());
    }
    public static void clear() { PREFERENCES.clear(); ACTIVE.clear(); cursor=0; }
    public static void logout(UUID player) { PREFERENCES.remove(player); ACTIVE.remove(player); }

    /** Server authority also protects snapshot visibility; client preferences contain no coordinates. */
    public static boolean eligible(ServerPlayer player, PortalPair pair, Endpoint source) {
        return source.equals(player.level().dimension().equals(Elsebase.DIMENSION)?pair.inner():pair.external())
                && player.level().dimension().equals(source.dimension()) && (pair.permanent() || pair.owner().equals(player.getUUID()))
                && player.getEyePosition().distanceToSqr(source.center())<=24*24
                && player.serverLevel().getBlockState(source.position()).is(Content.PORTAL.get());
    }
    public static void tick(MinecraftServer server) {
        int tick=server.getTickCount(); var budget=Settings.PREVIEW_BUDGET.get();
        if (budget==Budget.OFF) { ACTIVE.clear(); return; }
        int limit=switch(budget) { case LOW -> 8; case BALANCED -> 16; default -> 32; };
        if (tick%10==0) {
            PREFERENCES.entrySet().removeIf(e -> e.getValue().expires<tick || server.getPlayerList().getPlayer(e.getKey())==null);
            ACTIVE.keySet().removeIf(id -> !PREFERENCES.containsKey(id));
            if(ACTIVE.size()>limit) {
                var iterator=ACTIVE.keySet().iterator(); int retained=0;
                while(iterator.hasNext()) { iterator.next(); if(++retained>limit) iterator.remove(); }
            }
            var data=WorldState.get(server);
            for (var player : server.getPlayerList().getPlayers()) {
                var pref=PREFERENCES.get(player.getUUID());
                if (pref==null || pref.quality==0) { ACTIVE.remove(player.getUUID()); continue; }
                PortalPair selected=null; Endpoint source=null; double distance=24*24;
                for (var pair : data.pairs.values()) {
                    var candidate=player.level().dimension().equals(Elsebase.DIMENSION)?pair.inner():pair.external();
                    if (!eligible(player,pair,candidate)) continue;
                    var delta=candidate.center().add(0,1,0).subtract(player.getEyePosition());
                    if (delta.dot(player.getLookAngle()) < -1) continue;
                    double d=delta.lengthSqr();
                    if(d<distance) { selected=pair; source=candidate; distance=d; }
                }
                if (selected==null) { ACTIVE.remove(player.getUUID()); continue; }
                var target=PortalView.target(data,selected,source); var level=server.getLevel(target.dimension());
                if(level==null || level.getChunkSource().getChunkNow(target.position().getX()>>4,target.position().getZ()>>4)==null) { ACTIVE.remove(player.getUUID()); continue; }
                var old=ACTIVE.get(player.getUUID());
                if(old==null && ACTIVE.size()>=limit) continue;
                boolean changed=old==null || !old.scene.pair().equals(selected.id()) || !old.scene.source().equals(source) || !old.scene.target().equals(target);
                var biome=level.getBiome(target.position()).value();
                var scene=new PreviewProtocol.Scene(changed?++generation:old.scene.generation(),selected.id(),source,target,
                        level.getMinBuildHeight(),level.getHeight(),target.inner()&&!Settings.DARKNESS.get(),
                        level.dimensionType().hasSkyLight()?Math.max(0,1-level.getSkyDarken()/15f):0,level.dimensionType().ambientLight(),biome.getSkyColor());
                if(changed) { old=new Subscription(scene); ACTIVE.put(player.getUUID(),old); }
                old.scene=scene;
                PacketDistributor.sendToPlayer(player,scene);
            }
        }
        var viewers=new ArrayList<>(ACTIVE.keySet());
        int copies=switch(budget) { case LOW -> 1; case HIGH -> 4; default -> 2; };
        long start=System.nanoTime();
        for(int i=0;i<copies && !viewers.isEmpty();i++) {
            var id=viewers.get(Math.floorMod(cursor++,viewers.size())); var sub=ACTIVE.get(id); var player=server.getPlayerList().getPlayer(id);
            if(player==null || sub==null || sub.origins.isEmpty()) continue;
            // Revalidate on every copy, not only at interest refresh, to avoid stale ownership/anchor disclosure.
            var pair=WorldState.get(server).pairs.get(sub.scene.pair());
            if(pair==null || !eligible(player,pair,sub.scene.source()) || !PortalView.target(WorldState.get(server),pair,sub.scene.source()).equals(sub.scene.target())) {
                ACTIVE.remove(id); continue;
            }
            var level=server.getLevel(sub.scene.target().dimension());
            var origin=sub.origins.get(sub.next++%sub.origins.size());
            var snapshot=capture(level,sub.scene.generation(),origin);
            if(snapshot!=null) {
                var old=sub.previous.put(origin,snapshot);
                if(old==null || !Arrays.equals(old.states(),snapshot.states()) || !Arrays.equals(old.light(),snapshot.light()) || !Arrays.equals(old.biomes(),snapshot.biomes()))
                    PacketDistributor.sendToPlayer(player,snapshot);
            } else if(sub.previous.remove(origin)!=null) {
                PacketDistributor.sendToPlayer(player,new PreviewProtocol.Section(sub.scene.generation(),origin,new int[0],new byte[0],new int[0]));
            }
            if(System.nanoTime()-start>2_000_000) break;
        }
    }
    /** A bounded snapshot of a loaded section; null explicitly means unavailable, never air. */
    public static PreviewProtocol.Section capture(ServerLevel level, long generation, BlockPos origin) {
        if(level==null || origin.getY()<level.getMinBuildHeight() || origin.getY()>=level.getMaxBuildHeight()) return null;
        var chunk=level.getChunkSource().getChunkNow(origin.getX()>>4,origin.getZ()>>4);
        if(chunk==null) return null;
        var states=new int[4096]; var light=new byte[4096]; var biomes=new int[64];
        var pos=new BlockPos.MutableBlockPos(); var registry=level.registryAccess().registryOrThrow(Registries.BIOME);
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            int i=(y*16+z)*16+x; pos.set(origin.getX()+x,origin.getY()+y,origin.getZ()+z);
            states[i]=Block.getId(chunk.getBlockState(pos));
            light[i]=(byte)(level.getBrightness(LightLayer.BLOCK,pos)|(level.getBrightness(LightLayer.SKY,pos)<<4));
            if((x&3)==0 && (y&3)==0 && (z&3)==0) biomes[((y>>2)*4+(z>>2))*4+(x>>2)]=registry.getId(level.getBiome(pos).value());
        }
        return new PreviewProtocol.Section(generation,origin,states,light,biomes);
    }
    public static void transfer(ServerPlayer player, Endpoint source, Endpoint target, net.minecraft.world.phys.Vec3 landing, float yaw, float pitch) {
        if(player.connection.hasChannel(PreviewProtocol.Transfer.TYPE))
            PacketDistributor.sendToPlayer(player,new PreviewProtocol.Transfer(UUID.randomUUID(),source,target,landing,yaw,pitch));
    }
}
