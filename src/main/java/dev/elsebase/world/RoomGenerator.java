package dev.elsebase.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsebase.Content;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Stacked structural generator with no ores, structures, carvers or ticking decoration. */
public final class RoomGenerator extends ChunkGenerator {
    public static final MapCodec<RoomGenerator> CODEC = RecordCodecBuilder.mapCodec(i ->
            i.group(Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome)).apply(i, RoomGenerator::new));
    private final Holder<Biome> biome;
    private volatile long seed;

    public RoomGenerator(Holder<Biome> biome) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
    }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup, RandomState random, long seed) {
        this.seed = seed;
        return ChunkGeneratorStructureState.createForFlat(random, seed, biomeSource, Stream.empty());
    }
    public BlockState original(int x, int y, int z) {
        return Content.structure(RoomLayout.material(seed, x, y, z));
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        var pos = new BlockPos.MutableBlockPos();
        var ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        var surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < RoomLayout.HEIGHT; y++) {
            BlockState state = original(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
            if (!state.isAir()) {
                chunk.setBlockState(pos.set(x, y, z), state, false);
                ocean.update(x, y, z, state);
                surface.update(x, y, z, state);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return RoomLayout.HEIGHT;
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] states = new BlockState[level.getHeight()];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        for (int y = Math.max(0, level.getMinBuildHeight()); y < Math.min(RoomLayout.HEIGHT, level.getMaxBuildHeight()); y++)
            states[y - level.getMinBuildHeight()] = original(x, y, z);
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }
    @Override public void buildSurface(WorldGenRegion level, StructureManager manager, RandomState random, ChunkAccess chunk) {}
    @Override public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes, StructureManager manager, ChunkAccess chunk, GenerationStep.Carving step) {}
    @Override public void spawnOriginalMobs(WorldGenRegion level) {}
    @Override public int getMinY() { return RoomLayout.MIN_Y; }
    @Override public int getGenDepth() { return RoomLayout.HEIGHT; }
    @Override public int getSeaLevel() { return 0; }
    @Override public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) { info.add("Elsebase: chunk-aligned workspace"); }
}
