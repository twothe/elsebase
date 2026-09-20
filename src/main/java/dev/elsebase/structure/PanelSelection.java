package dev.elsebase.structure;

import dev.elsebase.StructuralBlock;
import dev.elsebase.world.RoomLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared client/server geometric targeting, independent of block reach and missing room borders. */
public final class PanelSelection {
    private PanelSelection() {}

    /** Creation targets the virtual room shell. Removal can reach the exposed adjoining wall half. */
    public static StructuralEditor.Panel select(Level level, Vec3 feet, Vec3 eye, Vec3 look, boolean restore) {
        int cx = Math.floorDiv((int)Math.floor(feet.x),16), cz = Math.floorDiv((int)Math.floor(feet.z),16);
        int floor = RoomLayout.floorAt((int)Math.floor(feet.y));
        if (floor < 0 || floor >= RoomLayout.HEIGHT-1 || look.lengthSqr() < 0.5) return null;
        Vec3 end = eye.add(look.normalize().scale(40));
        StructuralEditor.Panel nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Direction side : Direction.values()) {
            if (look.x*side.getStepX()+look.y*side.getStepY()+look.z*side.getStepZ() <= 1.0e-7) continue;
            var panel = new StructuralEditor.Panel(cx,cz,side,floor);
            // Include corners and walkable border strips in target acquisition, not in wall edit footprints.
            AABB box = shellBounds(panel);
            var hit = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye,end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < distance) {
                nearest = panel; distance = eye.distanceToSqr(hit.get());
            }
        }
        if (nearest == null || !level.hasChunk(nearest.cellX(),nearest.cellZ())) return null;
        if (restore || nearest.side().getAxis()==Direction.Axis.Y || hitsStructure(level,nearest,eye,end)) return nearest;
        Direction side = nearest.side();
        var neighbor = new StructuralEditor.Panel(cx+side.getStepX(),cz+side.getStepZ(),side.getOpposite(),floor);
        if (level.hasChunk(neighbor.cellX(),neighbor.cellZ()) && hitsStructure(level,neighbor,eye,end)) return neighbor;
        return nearest;
    }

    /** Queue revalidation forbids edits after moving into another level or beyond the adjacent wall. */
    public static boolean withinReach(Vec3 feet, StructuralEditor.Panel panel) {
        int cx = Math.floorDiv((int)Math.floor(feet.x),16), cz = Math.floorDiv((int)Math.floor(feet.z),16);
        if (RoomLayout.floorAt((int)Math.floor(feet.y)) != panel.floor()) return false;
        if (cx==panel.cellX() && cz==panel.cellZ()) return true;
        Direction side = panel.side();
        return side.getAxis()!=Direction.Axis.Y && panel.cellX()+side.getStepX()==cx && panel.cellZ()+side.getStepZ()==cz;
    }

    private static AABB shellBounds(StructuralEditor.Panel panel) {
        int x = panel.cellX()*16, z = panel.cellZ()*16, floor = panel.floor(), ceiling = RoomLayout.ceilingAt(floor);
        return switch(panel.side()) {
            case DOWN -> new AABB(x,floor,z,x+16,floor+1,z+16);
            case UP -> new AABB(x,ceiling,z,x+16,ceiling+1,z+16);
            case WEST -> new AABB(x,floor+1,z,x+1,ceiling,z+16);
            case EAST -> new AABB(x+15,floor+1,z,x+16,ceiling,z+16);
            case NORTH -> new AABB(x,floor+1,z,x+16,ceiling,z+1);
            case SOUTH -> new AABB(x,floor+1,z+15,x+16,ceiling,z+16);
        };
    }

    private static boolean hitsStructure(Level level, StructuralEditor.Panel panel, Vec3 eye, Vec3 end) {
        // At most 98 wall blocks. Read loaded state only; preview never requests chunk generation.
        for (BlockPos pos : panel.positions(false)) {
            if (StructuralBlock.protectedStructure(level,level.getBlockState(pos))
                    && (new AABB(pos).contains(eye) || new AABB(pos).clip(eye,end).isPresent())) return true;
        }
        return false;
    }
}
