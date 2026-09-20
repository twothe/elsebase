package dev.elsebase.client.preview;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.elsebase.Elsebase;
import dev.elsebase.preview.ModelQuarantine;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.*;

/** Own framebuffer and meshes only. Never swaps Minecraft.level, world renderers, camera or dispatchers. */
public final class PreviewRenderer implements AutoCloseable {
    public static ShaderInstance shader;
    public static final ResourceLocation TEXTURE=Elsebase.id("dynamic/portal_view");
    private final Map<BlockPos,SceneMesh> meshes=new LinkedHashMap<>();
    private final Set<BlockPos> failedSections=new HashSet<>();
    private TextureTarget target;
    public boolean ready;
    public int renderedFrames;
    public long lastRenderNanos;
    private final long[] renderSamples=new long[256];
    private int sampleCount;
    private int vertexCount;
    /** Readback used by the opt-in real-client regression, never the normal render loop. */
    public boolean hasColorVariation() {
        if(target==null) return false;
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)) {
            int first=image.getPixelRGBA(0,0), different=0;
            for(int y=0;y<image.getHeight();y+=8) for(int x=0;x<image.getWidth();x+=8) if(image.getPixelRGBA(x,y)!=first) different++;
            return different>100;
        }
    }
    /** CPU-side render submission timing only; this is not a GPU completion measurement. */
    public double renderP95Millis() {
        var samples=Arrays.copyOf(renderSamples,Math.min(sampleCount,renderSamples.length)); Arrays.sort(samples);
        return samples.length==0?0:samples[(int)((samples.length-1)*.95)]/1_000_000.0;
    }
    public double averageBrightness() {
        if(target==null) return 0;
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)) {
            double sum=0; int count=0;
            for(int y=0;y<image.getHeight();y+=8) for(int x=0;x<image.getWidth();x+=8) {
                int p=image.getPixelRGBA(x,y); sum+=((p&255)+(p>>8&255)+(p>>16&255))/3.0; count++;
            }
            return sum/count;
        }
    }
    /** At most one section rebuild per client tick, with a hard scene geometry cap. */
    public void rebuild(PreviewScene scene) {
        if(scene.dirty.isEmpty()) return;
        var iterator=scene.dirty.iterator(); var origin=iterator.next(); iterator.remove();
        if(!scene.sections.containsKey(origin)) {
            var old=meshes.remove(origin); if(old!=null) { vertexCount-=old.vertices; old.close(); }
            failedSections.remove(origin); ready=false; return;
        }
        if(failedSections.contains(origin)) return;
        try {
            var next=SceneMesh.build(scene,origin); var old=meshes.get(origin);
            int count=vertexCount-(old==null?0:old.vertices)+next.vertices;
            if(count>400_000) { next.close(); throw new IllegalStateException("Portal scene geometry budget exceeded"); }
            meshes.put(origin,next); vertexCount=count; if(old!=null) old.close();
        } catch(RuntimeException | LinkageError error) {
            ModelQuarantine.rethrowFatal(error); failedSections.add(origin);
            ready=false; Elsebase.LOGGER.warn("Portal section {} unavailable for this scene; retaining fallback",origin,error);
        }
    }
    public void render(PreviewScene scene,Vec3 camera) {
        var projection=PortalProjection.create(camera,scene.description.source(),scene.description.target());
        int width=switch(PreviewSettings.QUALITY.get()) { case LOW -> 256; case HIGH -> 768; default -> 512; };
        render(scene,projection,width,width*2);
    }
    /** A new full-screen perspective at the actual landing pose, never a stretched doorway image. */
    public DynamicTexture captureArrival(PreviewScene scene,dev.elsebase.preview.PreviewProtocol.Transfer transfer) {
        if(!ready || !scene.description.target().dimension().equals(transfer.target().dimension())
                || scene.description.target().center().distanceToSqr(transfer.landing())>8*8) return null;
        var mc=Minecraft.getInstance();
        var eye=transfer.landing().add(0,mc.player.getEyeHeight(),0).subtract(Vec3.atLowerCornerOf(scene.description.target().position()));
        var forward=Vec3.directionFromRotation(transfer.pitch(),transfer.yaw());
        var view=new org.joml.Matrix4f().lookAt((float)eye.x,(float)eye.y,(float)eye.z,(float)(eye.x+forward.x),(float)(eye.y+forward.y),(float)(eye.z+forward.z),0,1,0);
        float aspect=mc.getWindow().getWidth()/(float)mc.getWindow().getHeight();
        var projection=new org.joml.Matrix4f().perspective((float)Math.toRadians(mc.options.fov().get()),aspect,.05f,96);
        int width=Math.min(1280,mc.getWindow().getWidth()),height=Math.max(1,Math.round(width/aspect));
        render(scene,new PortalProjection(view,projection,new org.joml.Vector4f(0,0,0,1),eye,1),width,height);
        return ready?new DynamicTexture(net.minecraft.client.Screenshot.takeScreenshot(target)):null;
    }
    private void render(PreviewScene scene,PortalProjection projection,int width,int height) {
        ready=false;
        var targetPos=scene.description.target().position();
        var central=new BlockPos(targetPos.getX()&~15,targetPos.getY()&~15,targetPos.getZ()&~15);
        if(shader==null || projection==null || !meshes.containsKey(central) || !failedSections.isEmpty()) return;
        // Save actual GL bindings: do not assume the main target is bound (Fabulous may use another target).
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int texture=RenderSystem.getShaderTexture(0); var previousShader=RenderSystem.getShader();
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend=GL11.glIsEnabled(GL11.GL_BLEND), cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float[] clearColor=new float[4]; GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clearColor);
        double clearDepth=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        int depthFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        long start=System.nanoTime();
        try {
            if(target==null || target.width!=width || target.height!=height) {
                if(target!=null) target.destroyBuffers();
                target=new TextureTarget(width,height,true,Minecraft.ON_OSX);
                Minecraft.getInstance().getTextureManager().register(TEXTURE,new BorrowedTexture(target.getColorTextureId()));
            }
            int color=scene.description.target().inner()?0x28232e:scene.description.skyColor();
            float sky=scene.description.target().inner()?(scene.description.bright()?1:Math.max(.03f,scene.description.ambient())):Math.max(.08f,scene.description.sky());
            float r=((color>>16)&255)/255f*sky,g=((color>>8)&255)/255f*sky,b=(color&255)/255f*sky;
            RenderSystem.disableScissor(); RenderSystem.depthMask(true);
            target.setClearColor(r,g,b,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.depthMask(true);
            RenderSystem.disableCull(); RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_BLOCKS);
            shader.safeGetUniform("ClipPlane").set(projection.clip());
            shader.safeGetUniform("SceneFog").set(r,g,b);
            for(var mesh : meshes.values()) if(mesh.buffer!=null) {
                mesh.buffer.bind(); mesh.buffer.drawWithShader(projection.view(),projection.projection(),shader);
            }
            RenderSystem.enableBlend(); RenderSystem.depthMask(false);
            var transparent=new ArrayList<>(meshes.entrySet());
            var worldEye=projection.eye().add(Vec3.atLowerCornerOf(scene.description.target().position()));
            transparent.sort(Comparator.comparingDouble((Map.Entry<BlockPos,SceneMesh> entry) -> Vec3.atCenterOf(entry.getKey().offset(8,8,8)).distanceToSqr(worldEye)).reversed());
            for(var entry : transparent) if(entry.getValue().translucent!=null) {
                var mesh=entry.getValue(); mesh.sort(projection.eye()); mesh.translucent.bind();
                mesh.translucent.drawWithShader(projection.view(),projection.projection(),shader);
            }
            ready=true; renderedFrames++;
        } finally {
            VertexBuffer.unbind();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            RenderSystem.setShaderTexture(0,texture); RenderSystem.setShader(() -> previousShader);
            if(depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if(blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if(cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if(scissor) com.mojang.blaze3d.platform.GlStateManager._enableScissorTest(); else RenderSystem.disableScissor();
            RenderSystem.depthMask(mask); RenderSystem.depthFunc(depthFunction);
            RenderSystem.clearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]); RenderSystem.clearDepth(clearDepth);
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            lastRenderNanos=System.nanoTime()-start;
            renderSamples[sampleCount++%renderSamples.length]=lastRenderNanos;
        }
    }
    @Override public void close() {
        ready=false; meshes.values().forEach(SceneMesh::close); meshes.clear(); failedSections.clear(); vertexCount=0;
        if(target!=null) { Minecraft.getInstance().getTextureManager().release(TEXTURE); target.destroyBuffers(); target=null; }
    }
    /** Texture manager borrows the framebuffer attachment; the framebuffer alone owns its deletion. */
    private static final class BorrowedTexture extends AbstractTexture {
        BorrowedTexture(int texture) { id=texture; }
        @Override public void load(ResourceManager resources) {}
        @Override public void releaseId() { id=-1; }
        @Override public void close() {}
    }
}
