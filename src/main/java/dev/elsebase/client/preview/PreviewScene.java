package dev.elsebase.client.preview;

import dev.elsebase.preview.PreviewProtocol;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/** Read-only section cache. Missing data is opaque, preventing previews from exposing unloaded space. */
public final class PreviewScene implements BlockAndTintGetter {
    public PreviewProtocol.Scene description;
    public final Map<BlockPos,PreviewProtocol.Section> sections=new LinkedHashMap<>();
    public final Set<BlockPos> dirty=new LinkedHashSet<>();
    private final Registry<Biome> biomes;
    public PreviewScene(PreviewProtocol.Scene description, RegistryAccess registries) { this.description=description; biomes=registries.registryOrThrow(Registries.BIOME); }
    public boolean accept(PreviewProtocol.Section section) {
        var target=description.target().position(); var p=section.origin();
        if(section.generation()!=description.generation() || Math.abs((p.getX()>>4)-(target.getX()>>4))>1
                || Math.abs((p.getY()>>4)-(target.getY()>>4))>1 || Math.abs((p.getZ()>>4)-(target.getZ()>>4))>1
                || p.getY()<getMinBuildHeight() || p.getY()>=getMaxBuildHeight()) return false;
        if(!sections.containsKey(p) && sections.size()>=PreviewProtocol.MAX_SECTIONS) return false;
        if(section.states().length==0) sections.remove(p); else sections.put(p,section);
        dirty.add(p);
        for(var direction : Direction.values()) { var neighbor=p.relative(direction,16); if(sections.containsKey(neighbor)) dirty.add(neighbor); }
        return true;
    }
    private PreviewProtocol.Section section(BlockPos pos) { return sections.get(new BlockPos(pos.getX()&~15,pos.getY()&~15,pos.getZ()&~15)); }
    private static int index(BlockPos p) { return ((p.getY()&15)*16+(p.getZ()&15))*16+(p.getX()&15); }
    @Override public BlockState getBlockState(BlockPos pos) {
        var s=section(pos); return s==null?Blocks.BEDROCK.defaultBlockState():Block.stateById(s.states()[index(pos)]);
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    @Override public int getHeight() { return description.height(); }
    @Override public int getMinBuildHeight() { return description.minY(); }
    @Override public float getShade(Direction direction, boolean shade) {
        if(!shade) return 1;
        return switch(direction) { case DOWN -> .5f; case UP -> 1f; case NORTH,SOUTH -> .8f; default -> .6f; };
    }
    @Override public int getBrightness(LightLayer layer,BlockPos pos) {
        var s=section(pos); if(s==null) return 0;
        int light=s.light()[index(pos)]&255; return layer==LightLayer.SKY?light>>4:light&15;
    }
    @Override public int getRawBrightness(BlockPos pos,int amount) { return Math.max(getBrightness(LightLayer.BLOCK,pos),getBrightness(LightLayer.SKY,pos)-amount); }
    @Override public LevelLightEngine getLightEngine() { throw new UnsupportedOperationException("Read-only preview supplies sampled lighting, not a simulated light engine"); }
    @Override public int getBlockTint(BlockPos pos,ColorResolver resolver) {
        var s=section(pos); if(s==null) return 0xffffff;
        int i=(((pos.getY()&15)>>2)*4+((pos.getZ()&15)>>2))*4+((pos.getX()&15)>>2);
        var biome=biomes.byId(s.biomes()[i]); return biome==null?0xffffff:resolver.getColor(biome,pos.getX(),pos.getZ());
    }
    public float brightness(int block,int sky) {
        if(description.bright()) return 1;
        float level=Math.max(block/240f,sky/240f*description.sky());
        float vanilla=level/(4-3*level);
        return Math.max(description.ambient(),.03f+.97f*vanilla);
    }
}
