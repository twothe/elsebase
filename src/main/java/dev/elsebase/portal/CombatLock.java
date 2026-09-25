package dev.elsebase.portal;

import dev.elsebase.Settings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;

/** Incoming attacker-attributed hits delay instant summons; periodic/environmental damage does not renew the lock. */
public final class CombatLock {
    private static final String LAST_ATTACK = "elsebase_last_attack";
    private CombatLock() {}

    /** Called before mitigation, so a shield or armor does not make an incoming attack disappear. */
    public static void attacked(ServerPlayer player, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity) || source.getEntity() == player
                || source.getDirectEntity() instanceof AreaEffectCloud
                || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER)) return;
        player.getPersistentData().putLong(LAST_ATTACK, player.server.overworld().getGameTime());
    }

    /** World time is shared across dimensions and persists across logout/restart; death starts a fresh entity. */
    public static boolean blocked(ServerPlayer player) {
        int seconds = Settings.COMBAT_LOCK_SECONDS.get();
        var data = player.getPersistentData();
        if (seconds == 0 || !data.contains(LAST_ATTACK, net.minecraft.nbt.Tag.TAG_LONG)) return false;
        long elapsed = player.server.overworld().getGameTime() - data.getLong(LAST_ATTACK);
        return elapsed >= 0 && elapsed < seconds * 20L;
    }
}
