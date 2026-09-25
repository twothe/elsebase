package dev.elsebase.portal;

import dev.elsebase.Elsebase;
import dev.elsebase.Settings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/** Opt-in first-join placement, with persisted retry/respawn policy and no invented outside return. */
public final class BackdoorStart {
    private static final String PENDING = "elsebase_start_pending";
    private static final String STARTED = "elsebase_started_in_backdoor";
    private BackdoorStart() {}

    private static CompoundTag flags(ServerPlayer player) {
        var root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND))
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Existing home reservations distinguish returning 1.0 players without a save-format migration. */
    public static boolean login(ServerPlayer player) {
        if (!Settings.START_IN_BACKDOOR.get()) return true;
        var flags = flags(player);
        if (!flags.getBoolean(PENDING) && (flags.getBoolean(STARTED)
                || WorldState.get(player.server).homes.containsKey(player.getUUID()))) return true;
        flags.putBoolean(PENDING, true);
        Vec3 landing = AnchorArrival.resolve(player);
        if (landing == null) { unavailable(player); return false; }
        var level = player.server.getLevel(Elsebase.DIMENSION);
        player.stopRiding();
        player.teleportTo(level, landing.x, landing.y, landing.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
        player.setRespawnPosition(Elsebase.DIMENSION, net.minecraft.core.BlockPos.containing(landing), player.getYRot(), true, false);
        flags.putBoolean(STARTED, true); flags.remove(PENDING);
        InstantExpiry.residence(player);
        return true;
    }

    /** Repair personal-anchor respawns; other valid choices win, while missing spawns recover to the anchor. */
    public static void respawn(PlayerRespawnPositionEvent event) {
        var player = (ServerPlayer) event.getEntity();
        if (!Settings.START_IN_BACKDOOR.get() || event.isFromEndFight() || !flags(player).getBoolean(STARTED)
                || !event.getDimensionTransition().equals(event.getOriginalDimensionTransition())) return;
        var home = WorldState.get(player.server).homes.get(player.getUUID());
        boolean personalAnchor = home != null && player.getRespawnDimension().equals(Elsebase.DIMENSION)
                && home.anchor().equals(player.getRespawnPosition());
        if (!personalAnchor && !event.getOriginalDimensionTransition().missingRespawnBlock() && player.getRespawnPosition() != null) return;
        Vec3 landing = AnchorArrival.resolve(player);
        if (landing == null) { unavailable(player); return; }
        var original = event.getDimensionTransition();
        event.setDimensionTransition(new DimensionTransition(player.server.getLevel(Elsebase.DIMENSION), landing,
                Vec3.ZERO, original.yRot(), original.xRot(), original.postDimensionTransition()));
        player.setRespawnPosition(Elsebase.DIMENSION, net.minecraft.core.BlockPos.containing(landing), original.yRot(), true, false);
        event.setCopyOriginalSpawnPosition(true);
    }

    private static void unavailable(ServerPlayer player) {
        flags(player).putBoolean(PENDING, true);
        Elsebase.LOGGER.warn("Backdoor start/respawn blocked for {}; clear the player's anchor and support", player.getUUID());
        player.connection.disconnect(Component.translatable("elsebase.message.backdoor_start_blocked"));
    }
}
