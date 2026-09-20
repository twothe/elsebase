package dev.elsebase.preview;

import dev.elsebase.Elsebase;
import dev.elsebase.portal.Endpoint;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Block;

/** Dimension-addressed, bounded read-only snapshots. No block-entity NBT or client-selected destinations. */
public final class PreviewProtocol {
    public static final int SECTION_SIZE = 4096, MAX_SECTIONS = 27;
    // Installed only by the physical client entry point; dedicated servers never link client classes.
    public static Consumer<Scene> sceneReceiver = value -> {};
    public static Consumer<Section> sectionReceiver = value -> {};
    public static Consumer<Transfer> transferReceiver = value -> {};
    private PreviewProtocol() {}

    private static void endpoint(RegistryFriendlyByteBuf b, Endpoint e) {
        b.writeResourceKey(e.dimension()); b.writeBlockPos(e.position()); b.writeByte(e.facing().get2DDataValue());
    }
    private static Endpoint endpoint(RegistryFriendlyByteBuf b) {
        var dimension = b.readResourceKey(Registries.DIMENSION);
        var pos = b.readBlockPos(); int direction = b.readUnsignedByte();
        if (direction > 3) throw new IllegalArgumentException("Invalid preview direction");
        return new Endpoint(dimension,pos,Direction.from2DDataValue(direction));
    }
    public record Preference(int quality, boolean restart) implements CustomPacketPayload {
        public static final Type<Preference> TYPE = new Type<>(Elsebase.id("preview_preference"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Preference> CODEC = StreamCodec.of(
                (b,p) -> { b.writeByte(p.quality); b.writeBoolean(p.restart); }, b -> new Preference(b.readUnsignedByte(),b.readBoolean()));
        public Preference { if (quality < 0 || quality > 3) throw new IllegalArgumentException("Invalid preview quality"); }
        @Override public Type<?> type() { return TYPE; }
    }
    public record Scene(long generation, UUID pair, Endpoint source, Endpoint target, int minY, int height,
                        boolean bright, float sky, float ambient, int skyColor) implements CustomPacketPayload {
        public static final Type<Scene> TYPE = new Type<>(Elsebase.id("preview_scene"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Scene> CODEC = StreamCodec.of((b,p) -> {
            b.writeLong(p.generation); b.writeUUID(p.pair); endpoint(b,p.source); endpoint(b,p.target);
            b.writeInt(p.minY); b.writeInt(p.height); b.writeBoolean(p.bright); b.writeFloat(p.sky); b.writeFloat(p.ambient); b.writeInt(p.skyColor);
        }, b -> new Scene(b.readLong(),b.readUUID(),endpoint(b),endpoint(b),b.readInt(),b.readInt(),b.readBoolean(),b.readFloat(),b.readFloat(),b.readInt()));
        public Scene {
            if (height <= 0 || height > 4096 || !Float.isFinite(sky) || sky < 0 || sky > 1 || !Float.isFinite(ambient) || ambient < 0 || ambient > 1)
                throw new IllegalArgumentException("Invalid preview dimension metadata");
        }
        @Override public Type<?> type() { return TYPE; }
    }
    public record Section(long generation, BlockPos origin, int[] states, byte[] light, int[] biomes) implements CustomPacketPayload {
        public static final Type<Section> TYPE = new Type<>(Elsebase.id("preview_section"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Section> CODEC = StreamCodec.of((b,p) -> {
            b.writeLong(p.generation); b.writeBlockPos(p.origin);
            b.writeBoolean(p.states.length!=0); if(p.states.length==0) return;
            for (int id : p.states) b.writeVarInt(id);
            b.writeBytes(p.light); for (int id : p.biomes) b.writeVarInt(id);
        }, b -> {
            long generation = b.readLong(); var origin = b.readBlockPos();
            if(!b.readBoolean()) return new Section(generation,origin,new int[0],new byte[0],new int[0]);
            var states = new int[SECTION_SIZE];
            for (int i=0;i<states.length;i++) {
                states[i]=b.readVarInt();
                if (states[i]<0 || states[i]>=Block.BLOCK_STATE_REGISTRY.size()) throw new IllegalArgumentException("Unknown preview block state");
            }
            byte[] light = new byte[SECTION_SIZE]; b.readBytes(light); int[] biomes = new int[64];
            int count = b.registryAccess().registryOrThrow(Registries.BIOME).size();
            for (int i=0;i<64;i++) { biomes[i]=b.readVarInt(); if (biomes[i]<0 || biomes[i]>=count) throw new IllegalArgumentException("Unknown preview biome"); }
            return new Section(generation,origin,states,light,biomes);
        });
        public Section {
            if (!(states.length==SECTION_SIZE && light.length==SECTION_SIZE && biomes.length==64 || states.length==0 && light.length==0 && biomes.length==0)
                    || (origin.getX()&15)!=0 || (origin.getY()&15)!=0 || (origin.getZ()&15)!=0)
                throw new IllegalArgumentException("Invalid preview section");
        }
        @Override public Type<?> type() { return TYPE; }
    }
    public record Transfer(UUID id, Endpoint source, Endpoint target, net.minecraft.world.phys.Vec3 landing, float yaw, float pitch) implements CustomPacketPayload {
        public static final Type<Transfer> TYPE = new Type<>(Elsebase.id("portal_transfer"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Transfer> CODEC = StreamCodec.of((b,p) -> {
            b.writeUUID(p.id); endpoint(b,p.source); endpoint(b,p.target);
            b.writeDouble(p.landing.x); b.writeDouble(p.landing.y); b.writeDouble(p.landing.z); b.writeFloat(p.yaw); b.writeFloat(p.pitch);
        }, b -> new Transfer(b.readUUID(),endpoint(b),endpoint(b),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readFloat()));
        public Transfer {
            if(!Double.isFinite(landing.x) || !Double.isFinite(landing.y) || !Double.isFinite(landing.z) || !Float.isFinite(yaw) || !Float.isFinite(pitch))
                throw new IllegalArgumentException("Invalid portal transfer pose");
        }
        @Override public Type<?> type() { return TYPE; }
    }
}
