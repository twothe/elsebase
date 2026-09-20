package dev.elsebase.client.preview;

import com.mojang.blaze3d.vertex.*;
import dev.elsebase.Elsebase;
import dev.elsebase.portal.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** One non-ticking two-sided aperture per doorway; shader fallback stays in the ordinary entity render pipeline. */
public final class PortalSurfaceRenderer implements BlockEntityRenderer<PortalSurface> {
    public static final ModelResourceLocation SURFACE=ModelResourceLocation.standalone(Elsebase.id("block/portal_surface"));
    public PortalSurfaceRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(PortalSurface surface) {
        return new net.minecraft.world.phys.AABB(surface.getBlockPos()).expandTowards(0,1,0);
    }
    @Override public void render(PortalSurface surface,float tick,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var state=surface.getBlockState();
        if(state.getValue(PortalBlock.HALF)!=DoubleBlockHalf.LOWER || surface.getLevel()==null) return;
        var source=new Endpoint(surface.getLevel().dimension(),surface.getBlockPos(),state.getValue(PortalBlock.FACING));
        boolean live=PreviewClient.live(source);
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var normal=Vec3.atLowerCornerOf(source.facing().getNormal());
        int side=camera.subtract(source.center()).dot(normal)>=0?1:-1;
        var right=Vec3.atLowerCornerOf(source.right().getNormal()).scale(side*.4375);
        var center=new Vec3(.5,0,.5);
        var left=center.add(right); var rightPoint=center.subtract(right);
        var sprite=Minecraft.getInstance().getModelManager().getModel(SURFACE).getParticleIcon();
        float u0=live?0:sprite.getU0(),u1=live?1:sprite.getU1();
        float bottom=live?0:sprite.getV1(),top=live?1:sprite.getV0();
        var buffer=buffers.getBuffer(live?PortalRenderType.VIEW:RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        vertex(buffer,pose,left.x,.03125,left.z,u0,bottom,light,normal);
        vertex(buffer,pose,rightPoint.x,.03125,rightPoint.z,u1,bottom,light,normal);
        vertex(buffer,pose,rightPoint.x,1.9375,rightPoint.z,u1,top,light,normal);
        vertex(buffer,pose,left.x,1.9375,left.z,u0,top,light,normal);
    }
    private static void vertex(VertexConsumer buffer,PoseStack pose,double x,double y,double z,float u,float v,int light,Vec3 normal) {
        buffer.addVertex(pose.last(),(float)x,(float)y,(float)z).setColor(255,255,255,255).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),(float)normal.x,0,(float)normal.z);
    }
}
