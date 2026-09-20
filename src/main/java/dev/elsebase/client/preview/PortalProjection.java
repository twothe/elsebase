package dev.elsebase.client.preview;

import dev.elsebase.portal.Endpoint;
import dev.elsebase.preview.PortalView;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

/** Off-axis portal window projection in destination-local coordinates; independent of the current world's camera state. */
public record PortalProjection(Matrix4f view,Matrix4f projection,Vector4f clip,Vec3 eye,int side) {
    public static PortalProjection create(Vec3 camera,Endpoint source,Endpoint target) {
        var sourceNormal=Vec3.atLowerCornerOf(source.facing().getNormal());
        int side=camera.subtract(source.center()).dot(sourceNormal)>=0?1:-1;
        var normal=Vec3.atLowerCornerOf(target.facing().getNormal()).scale(side);
        var eye=PortalView.transform(camera,source,target).subtract(Vec3.atLowerCornerOf(target.position()));
        var center=target.center().subtract(Vec3.atLowerCornerOf(target.position()));
        double distance=center.subtract(eye).dot(normal);
        if(distance<.025) return null;
        var right=normal.cross(new Vec3(0,1,0));
        float ex=(float)eye.subtract(center).dot(right), ey=(float)eye.y;
        float near=.01f, scale=near/(float)distance;
        var view=new Matrix4f().lookAt((float)eye.x,(float)eye.y,(float)eye.z,
                (float)(eye.x+normal.x),(float)eye.y,(float)(eye.z+normal.z),0,1,0);
        var projection=new Matrix4f().frustum((-.4375f-ex)*scale,(.4375f-ex)*scale,(.03125f-ey)*scale,(1.9375f-ey)*scale,near,96);
        var clip=new Vector4f((float)normal.x,0,(float)normal.z,(float)-center.dot(normal));
        return new PortalProjection(view,projection,clip,eye,side);
    }
}
