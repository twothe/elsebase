package dev.elsebase.portal;

import dev.elsebase.Content;
import dev.elsebase.Elsebase;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** Persisted outside-only inactivity timeout. Offline inside owners retain their return indefinitely. */
public final class InstantExpiry {
    public static final long TIMEOUT = 1200;
    public record State(long expires, boolean inside) {
        public boolean expired(long now) { return !inside && now>=expires; }
    }
    private InstantExpiry() {}
    /** Successful travel or summon refreshes the owner's personal pair, including uses of permanent doorways. */
    public static void used(ServerPlayer player) {
        var data = WorldState.get(player.server);
        var pair = data.instant(player.getUUID());
        if (pair==null) return;
        data.lifetimes.put(pair.id(),new State(player.server.overworld().getGameTime()+TIMEOUT,
                player.level().dimension().equals(Elsebase.DIMENSION)));
        data.setDirty();
    }
    /** Dimension changes via death/other mods also protect an inside owner and start a fresh outside timeout. */
    public static void residence(ServerPlayer player) {
        var data = WorldState.get(player.server);
        var pair = data.instant(player.getUUID());
        if (pair==null) return;
        var state = data.lifetimes.get(pair.id());
        if (state==null || state.inside()!=player.level().dimension().equals(Elsebase.DIMENSION)) used(player);
    }
    /** At most one expired pair per tick; do not generate chunks merely to remove visual clutter. */
    public static void tick(MinecraftServer server) {
        var data = WorldState.get(server);
        for (var pair : List.copyOf(data.pairs.values())) {
            if (pair.permanent()) continue;
            var player = server.getPlayerList().getPlayer(pair.owner());
            var state = data.lifetimes.get(pair.id());
            if (player!=null && (state==null || state.inside()!=player.level().dimension().equals(Elsebase.DIMENSION))) {
                used(player); state=data.lifetimes.get(pair.id());
            }
            // Existing saves without lifetime data are initialized when their owner next becomes known.
            if (state==null || !state.expired(server.overworld().getGameTime())) continue;
            data.remove(pair.id());
            for (var endpoint : List.of(pair.inner(),pair.external())) {
                var level = server.getLevel(endpoint.dimension());
                if (level==null) continue;
                var chunk = level.getChunkSource().getChunkNow(endpoint.position().getX()>>4,endpoint.position().getZ()>>4);
                if (chunk==null) continue; // Registry removal makes reconciliation retire unloaded surfaces on load.
                for (var pos : endpoint.blocks()) if (data.at(level.dimension(),pos)==null && level.getBlockState(pos).is(Content.PORTAL.get()))
                    level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            }
            return;
        }
    }
}
