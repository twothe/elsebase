package dev.elsebase.client;

import dev.elsebase.*;
import dev.elsebase.template.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.*;

/** Standard chunk-baked structural faces. No block entities, per-frame tessellation or global-world swapping. */
@EventBusSubscriber(modid=Elsebase.ID,value=Dist.CLIENT)
public final class TemplateModels {
    private static final ModelProperty<int[]> FACES=new ModelProperty<>();
    private static final Map<Pattern.Material,BlockState> STATES=new ConcurrentHashMap<>();
    private static final Set<String> FAILED=ConcurrentHashMap.newKeySet();
    private static final Map<Pattern.Material,Boolean> SUPPORTED=new ConcurrentHashMap<>();
    private static volatile Map<ModelResourceLocation,BakedModel> originals=Map.of();
    private record Face(int state,Direction direction) {}
    private static final Map<Face,List<BakedQuad>> QUADS=new ConcurrentHashMap<>();
    public static volatile boolean inBackdoor;
    public static final Map<Long,String[]> COLUMNS=new ConcurrentHashMap<>();
    public static final Map<String,Pattern> PATTERNS=new ConcurrentHashMap<>();
    public static void clear() { COLUMNS.clear(); PATTERNS.clear(); inBackdoor=false; STATES.clear(); }
    public static Pattern pattern(BlockPos pos,Direction face) {
        var column=COLUMNS.get(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));
        if(column==null || pos.getY()<0 || pos.getY()>=128) return null;
        return PATTERNS.get(column[SurfaceAddress.at(pos,face).index()]);
    }
    public static BlockState material(Pattern.Material material) { return STATES.computeIfAbsent(material,Materials::resolve); }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        originals=Map.copyOf(event.getModels()); QUADS.clear(); STATES.clear(); FAILED.clear(); SUPPORTED.clear();
        event.getModels().replaceAll((key,model) -> {
            var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(key.id());
            return block instanceof StructuralBlock?new Wrapper(model):model;
        });
    }
    private static List<BakedQuad> face(int id,Direction side) {
        return QUADS.computeIfAbsent(new Face(id,side),key -> {
            var state=Block.stateById(id); var model=originals.get(BlockModelShaper.stateToModelLocation(state));
            if(model==null) throw new Pattern.Rejected("No baked model");
            var quads=new ArrayList<>(model.getQuads(state,side,RandomSource.create(0),ModelData.EMPTY,net.minecraft.client.renderer.RenderType.solid()));
            for(var quad:model.getQuads(state,null,RandomSource.create(0),ModelData.EMPTY,net.minecraft.client.renderer.RenderType.solid())) if(quad.getDirection()==side) quads.add(quad);
            if(quads.isEmpty() || quads.size()>32 || quads.stream().anyMatch(BakedQuad::isTinted)) throw new Pattern.Rejected("Unsupported cube material model");
            for(var quad:quads) for(int i=0;i<4;i++) {
                int stride=quad.getVertices().length/4;
                for(int axis=0;axis<3;axis++) { float coordinate=Float.intBitsToFloat(quad.getVertices()[i*stride+axis]); if(!Float.isFinite(coordinate) || coordinate<-.001f || coordinate>1.001f) throw new Pattern.Rejected("Model exceeds cube bounds"); }
            }
            return List.copyOf(quads);
        });
    }
    public static boolean supported(Pattern.Material material) {
        return SUPPORTED.computeIfAbsent(material,key -> {
            try { var state=material(key); for(var side:Direction.values()) face(Block.getId(state),side); return true; }
            catch(RuntimeException | LinkageError error) { dev.elsebase.preview.ModelQuarantine.rethrowFatal(error); return false; }
        });
    }
    private static final class Wrapper extends net.neoforged.neoforge.client.model.BakedModelWrapper<BakedModel> {
        Wrapper(BakedModel original) { super(original); }
        @Override public ModelData getModelData(BlockAndTintGetter world,BlockPos pos,BlockState state,ModelData incoming) {
            boolean enabled=world instanceof dev.elsebase.client.preview.PreviewScene scene?scene.description.target().inner():inBackdoor;
            if(!enabled || !state.getValue(StructuralBlock.STRUCTURAL)) return ModelData.EMPTY;
            var faces=new int[6]; Arrays.fill(faces,-1);
            for(var side:Direction.values()) {
                var pattern=pattern(pos,side); if(pattern==null) continue; var address=SurfaceAddress.at(pos,side); var material=pattern.at(address.u(),address.v());
                if(FAILED.contains(material.block())) continue;
                try { faces[side.ordinal()]=Block.getId(MaterialOrientation.toWorld(material(material),address.panel().side())); }
                catch(RuntimeException | LinkageError error) { failed(material.block(),error); }
            }
            return ModelData.builder().with(FACES,faces).build();
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,net.minecraft.client.renderer.RenderType layer) {
            var faces=data.get(FACES); if(faces==null || side==null || faces[side.ordinal()]<0) return super.getQuads(state,side,random,data,layer);
            int id=faces[side.ordinal()]; String key=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(Block.stateById(id).getBlock()).toString();
            if(!FAILED.contains(key)) try { return face(id,side); } catch(RuntimeException | LinkageError error) { failed(key,error); }
            return super.getQuads(state,side,random,data,layer);
        }
    }
    private static void failed(String id,Throwable error) {
        dev.elsebase.preview.ModelQuarantine.rethrowFatal(error);
        if(FAILED.add(id) && FAILED.size()<=16) Elsebase.LOGGER.warn("Template material {} disabled for this resource session; using structural fallback",id,error);
    }
    private TemplateModels() {}
}
