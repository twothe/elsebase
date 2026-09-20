package dev.elsebase.preview;

import dev.elsebase.portal.*;
import net.minecraft.world.phys.Vec3;

/** Shared directed frame transform; personal previews resolve the current home, not a recalled frame. */
public final class PortalView {
    private PortalView() {}
    public static Endpoint target(WorldState state, PortalPair pair, Endpoint source) {
        if (source.inner()) return pair.external();
        var home = state.homes.get(pair.owner());
        return !pair.permanent() && home!=null ? home.reference() : pair.inner();
    }
    public static float rotation(Endpoint source, Endpoint target) {
        return target.facing().toYRot()-source.facing().toYRot()+180;
    }
    public static Vec3 transform(Vec3 position, Endpoint source, Endpoint target) {
        // Cardinal basis arithmetic avoids vanilla's float sine-table error at quadrant boundaries.
        var local=position.subtract(source.center());
        double lateral=local.dot(Vec3.atLowerCornerOf(source.right().getNormal()));
        double depth=local.dot(Vec3.atLowerCornerOf(source.facing().getNormal()));
        return target.center().add(0,local.y,0)
                .subtract(Vec3.atLowerCornerOf(target.right().getNormal()).scale(lateral))
                .subtract(Vec3.atLowerCornerOf(target.facing().getNormal()).scale(depth));
    }
}
