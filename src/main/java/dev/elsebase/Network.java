package dev.elsebase;

import dev.elsebase.portal.Portals;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client intent and server lighting policy; coordinates, ownership and outcomes are never accepted from clients. */
public final class Network {
    private static Boolean lastServerDarkness;
    public record Lighting(boolean darkness) implements CustomPacketPayload {
        public static final Type<Lighting> TYPE = new Type<>(Elsebase.id("lighting"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Lighting> CODEC = StreamCodec.of(
                (buffer, value) -> buffer.writeBoolean(value.darkness()), buffer -> new Lighting(buffer.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Action() implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Elsebase.id("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (buffer, value) -> {}, buffer -> new Action());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3");
        registrar.playToServer(Action.TYPE, Action.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                Portals.request(player);
            }
        });
        registrar.playToClient(Lighting.TYPE, Lighting.CODEC, (payload, context) -> LightingPolicy.receive(payload.darkness()));
    }
    public static void syncLighting(ServerPlayer player) {
        // Synthetic/fake-player connections have no negotiated client payload channels.
        if (!player.connection.hasChannel(Lighting.TYPE)) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new Lighting(Settings.DARKNESS.get()));
    }
    /** Only changed server policy is broadcast; clients cannot change multiplayer brightness by editing their file. */
    public static void refreshLighting(net.minecraft.server.MinecraftServer server) {
        boolean darkness = Settings.DARKNESS.get();
        if (lastServerDarkness == null || lastServerDarkness != darkness) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) syncLighting(player);
            lastServerDarkness = darkness;
        }
    }
    public static void serverStopped() { lastServerDarkness = null; }
    private Network() {}
}
