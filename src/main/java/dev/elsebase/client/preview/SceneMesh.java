package dev.elsebase.client.preview;

import com.mojang.blaze3d.vertex.*;
import dev.elsebase.Elsebase;
import dev.elsebase.preview.ModelQuarantine;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Incremental isolated baked-model tessellation with atomic per-block geometry and session quarantine. */
public final class SceneMesh implements AutoCloseable {
    public static final int MAX_VERTICES=65_536;
    private static int QUARANTINE_COUNT;
    public static final ModelQuarantine<ResourceLocation> QUARANTINE=new ModelQuarantine<>((key,error) -> {
        if(QUARANTINE_COUNT++<16) Elsebase.LOGGER.warn("Skipping failed preview model {} for this resource session; gameplay is unaffected",key,error);
        else if(QUARANTINE_COUNT==17) Elsebase.LOGGER.warn("Further preview model errors suppressed; quarantined model count remains available in diagnostics");
    });
    public final VertexBuffer buffer;
    public final VertexBuffer translucent;
    private final MeshData.SortState sortState;
    private net.minecraft.world.phys.Vec3 sortedEye;
    public final int vertices;
    private SceneMesh(VertexBuffer buffer,VertexBuffer translucent,MeshData.SortState sortState,int vertices) { this.buffer=buffer;this.translucent=translucent;this.sortState=sortState;this.vertices=vertices; }
    public void sort(net.minecraft.world.phys.Vec3 eye) {
        if(sortState==null || sortedEye!=null && sortedEye.distanceToSqr(eye)<.0025) return;
        try(var bytes=new ByteBufferBuilder(sortState.centroids().length*24)) {
            translucent.bind(); translucent.uploadIndexBuffer(sortState.buildSortedIndexBuffer(bytes,VertexSorting.byDistance((float)eye.x,(float)eye.y,(float)eye.z)));
            sortedEye=eye;
        }
    }
    public static void resetFailures() { QUARANTINE.clear(); QUARANTINE_COUNT=0; }
    public static SceneMesh build(PreviewScene scene,BlockPos origin) {
        var dispatcher=Minecraft.getInstance().getBlockRenderer();
        var random=RandomSource.create(); var geometry=new ArrayList<Vertex>();
        var sceneOrigin=scene.description.target().position();
        for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            var pos=origin.offset(x,y,z); var state=scene.getBlockState(pos);
            if(state.isAir()) continue;
            // The destination aperture is the window being looked through, not another visible portal surface.
            if(state.is(dev.elsebase.Content.PORTAL.get()) && (pos.equals(scene.description.target().position()) || pos.equals(scene.description.target().position().above()))) continue;
            var key=BuiltInRegistries.BLOCK.getKey(state.getBlock());
            var emitted=QUARANTINE.attempt(key,() -> {
                var sink=new Sink(scene); var pose=new PoseStack();
                pose.translate(pos.getX()-sceneOrigin.getX(),pos.getY()-sceneOrigin.getY(),pos.getZ()-sceneOrigin.getZ());
                if(state.getRenderShape()==RenderShape.MODEL) {
                    var model=dispatcher.getBlockModel(state);
                    var data=model.getModelData(scene,pos,state,ModelData.EMPTY);
                    for(var layer : model.getRenderTypes(state,random,data)) {
                        sink.translucent=layer!=net.minecraft.client.renderer.RenderType.solid() && layer!=net.minecraft.client.renderer.RenderType.cutout() && layer!=net.minecraft.client.renderer.RenderType.cutoutMipped();
                        dispatcher.renderBatched(state,pos,scene,pose,sink,true,random,data,layer);
                    }
                }
                if(!state.getFluidState().isEmpty()) {
                    sink.translucent=true;
                    sink.offsetX=origin.getX()-sceneOrigin.getX(); sink.offsetY=origin.getY()-sceneOrigin.getY(); sink.offsetZ=origin.getZ()-sceneOrigin.getZ();
                    dispatcher.renderLiquid(pos,scene,sink,state,state.getFluidState());
                }
                if(state.is(dev.elsebase.Content.PORTAL.get()) && state.getValue(dev.elsebase.portal.PortalBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                    // Target portals remain an animated placeholder; this pass never invokes a portal renderer.
                    var sprite=Minecraft.getInstance().getModelManager().getModel(PortalSurfaceRenderer.SURFACE).getParticleIcon();
                    var right=net.minecraft.world.phys.Vec3.atLowerCornerOf(state.getValue(dev.elsebase.portal.PortalBlock.FACING).getClockWise().getNormal()).scale(.4375);
                    var center=net.minecraft.world.phys.Vec3.atBottomCenterOf(pos).subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(sceneOrigin));
                    sink.offsetX=0; sink.offsetY=0; sink.offsetZ=0; sink.translucent=false;
                    for(int i=0;i<4;i++) {
                        boolean left=i==0 || i==3,top=i>=2; var point=center.add(right.scale(left?1:-1)).add(0,top?1.9375:.03125,0);
                        sink.addVertex((float)point.x,(float)point.y,(float)point.z).setUv(left?sprite.getU0():sprite.getU1(),top?sprite.getV0():sprite.getV1());
                    }
                }
                sink.finish();
                if(sink.vertices.size()%4!=0) throw new IllegalStateException("Preview model emitted incomplete quads");
                return sink.vertices;
            });
            if(emitted!=null) {
                if(geometry.size()+emitted.size()>MAX_VERTICES) throw new IllegalStateException("Preview section mesh exceeds bounded geometry budget");
                geometry.addAll(emitted);
            }
        }
        VertexBuffer solid=null,transparent=null;
        try {
            var opaque=upload(geometry.stream().filter(v -> !v.translucent).toList(),false); solid=opaque.buffer;
            var alpha=upload(geometry.stream().filter(v -> v.translucent).toList(),true); transparent=alpha.buffer;
            return new SceneMesh(solid,transparent,alpha.sort,geometry.size());
        } catch(RuntimeException | LinkageError failure) {
            if(solid!=null) solid.close(); if(transparent!=null) transparent.close(); throw failure;
        }
    }
    private record Uploaded(VertexBuffer buffer,MeshData.SortState sort) {}
    private static Uploaded upload(List<Vertex> geometry,boolean translucent) {
        if(geometry.isEmpty()) return new Uploaded(null,null);
        try(var bytes=new ByteBufferBuilder(geometry.size()*24)) {
            var builder=new BufferBuilder(bytes,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            for(var v : geometry) builder.addVertex(v.x,v.y,v.z).setUv(v.u,v.v).setColor(v.r,v.g,v.b,v.a);
            var mesh=builder.buildOrThrow(); var sort=translucent?mesh.sortQuads(bytes,VertexSorting.DISTANCE_TO_ORIGIN):null;
            var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
            try { buffer.bind(); buffer.upload(mesh); }
            catch(RuntimeException | LinkageError e) { buffer.close(); throw e; }
            finally { VertexBuffer.unbind(); }
            return new Uploaded(buffer,sort);
        }
    }
    @Override public void close() { if(buffer!=null) buffer.close(); if(translucent!=null) translucent.close(); }
    private static final class Vertex {
        float x,y,z,u,v; int r=255,g=255,b=255,a=255; int block=240,sky=240; boolean translucent;
    }
    private static final class Sink implements VertexConsumer {
        final List<Vertex> vertices=new ArrayList<>(); final PreviewScene scene;
        Vertex current; float offsetX,offsetY,offsetZ; boolean translucent;
        Sink(PreviewScene scene) { this.scene=scene; }
        void finish() {
            if(current==null) return;
            if(!Float.isFinite(current.x) || !Float.isFinite(current.y) || !Float.isFinite(current.z) || !Float.isFinite(current.u) || !Float.isFinite(current.v))
                throw new IllegalStateException("Preview model emitted non-finite vertices");
            float light=scene.brightness(current.block,current.sky);
            current.r=(int)(current.r*light); current.g=(int)(current.g*light); current.b=(int)(current.b*light);
            vertices.add(current); current=null;
        }
        @Override public VertexConsumer addVertex(float x,float y,float z) {
            finish(); if(vertices.size()>=8192) throw new IllegalStateException("Preview model exceeds per-block geometry budget");
            current=new Vertex(); current.x=x+offsetX; current.y=y+offsetY; current.z=z+offsetZ; current.translucent=translucent; return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { current.r=r;current.g=g;current.b=b;current.a=a;return this; }
        @Override public VertexConsumer setUv(float u,float v) { current.u=u;current.v=v;return this; }
        @Override public VertexConsumer setUv1(int u,int v) { return this; }
        @Override public VertexConsumer setUv2(int u,int v) { current.block=u;current.sky=v;return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
}
