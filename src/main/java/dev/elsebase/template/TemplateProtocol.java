package dev.elsebase.template;

import dev.elsebase.Elsebase;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Bounded opaque import envelope: semantic parsing happens after authenticated player identity is available. */
public final class TemplateProtocol {
    public static Consumer<Reply> receiver=message -> {};
    public record Request(String operation,String key,byte[] data) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(Elsebase.id("template_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p) -> { b.writeUtf(p.operation,24); b.writeUtf(p.key,128); b.writeByteArray(p.data); },b -> new Request(b.readUtf(24),b.readUtf(128),b.readByteArray(Pattern.MAX_BYTES)));
        @Override public Type<?> type() { return TYPE; }
    }
    public record Reply(String operation,String key,byte[] data) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(Elsebase.id("template_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=StreamCodec.of((b,p) -> { b.writeUtf(p.operation,24); b.writeUtf(p.key,128); b.writeByteArray(p.data); },b -> new Reply(b.readUtf(24),b.readUtf(128),b.readByteArray(Pattern.MAX_BYTES)));
        @Override public Type<?> type() { return TYPE; }
    }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Request.TYPE,Request.CODEC,(message,context) -> { if(context.player() instanceof net.minecraft.server.level.ServerPlayer player) TemplateServer.request(player,message); });
        registrar.playToClient(Reply.TYPE,Reply.CODEC,(message,context) -> receiver.accept(message));
    }
    private TemplateProtocol() {}
}
