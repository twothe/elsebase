package dev.elsebase.template;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Open material policy: technical shape/data restrictions plus an optional server-owned blocklist. */
public final class Materials {
    public static final TagKey<Block> BLOCKED=TagKey.create(net.minecraft.core.registries.Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("elsebase","template_material_blacklist"));
    private Materials() {}
    public static String problem(BlockState state) {
        if(state.is(BLOCKED)) return "Material is blocked by this server";
        if(state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.getRenderShape()!=RenderShape.MODEL
                || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO) || !state.canOcclude()) return "Material must be an opaque full cube without block entity or fluid";
        return null;
    }
    public static BlockState resolve(Pattern.Material material) {
        var id=ResourceLocation.parse(material.block());
        if(!BuiltInRegistries.BLOCK.containsKey(id)) throw new Pattern.Rejected("Missing material: "+id);
        var state=BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        for(var entry:material.properties().entrySet()) {
            var property=state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if(property==null) throw new Pattern.Rejected("Unknown property: "+entry.getKey());
            state=property(state,property,entry.getValue());
        }
        String problem=problem(state); if(problem!=null) throw new Pattern.Rejected(material.block()+": "+problem);
        return state;
    }
    private static <T extends Comparable<T>> BlockState property(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property,String value) {
        return state.setValue(property,property.getValue(value).orElseThrow(() -> new Pattern.Rejected("Invalid property value")));
    }
    public static Pattern.Material describe(BlockState state) {
        var properties=new TreeMap<String,String>(); state.getValues().forEach((key,value) -> properties.put(key.getName(),name(key,value)));
        return new Pattern.Material(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),properties);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static String name(net.minecraft.world.level.block.state.properties.Property property,Comparable value) { return property.getName(value); }
    public static void validate(Pattern pattern) { pattern.palette().forEach(Materials::resolve); }
}
