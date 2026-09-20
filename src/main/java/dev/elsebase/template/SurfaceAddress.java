package dev.elsebase.template;

import dev.elsebase.structure.StructuralEditor.Panel;
import dev.elsebase.world.RoomLayout;
import net.minecraft.core.*;

/** Shared face ownership and pattern coordinates, including opposite appearances on a shared slab. */
public record SurfaceAddress(Panel panel,int u,int v) {
    public int index() { return panel.floor()/8*6+panel.side().ordinal(); }
    public static SurfaceAddress at(BlockPos pos,Direction face) {
        int x=pos.getX()&15,z=pos.getZ()&15,y=pos.getY();
        int floor=RoomLayout.floorAt(y); Direction side;
        if(y%8==0 || y==127) {
            if(face==Direction.DOWN || y==127) { floor=RoomLayout.floorAt(y-1); side=Direction.UP; }
            else side=Direction.DOWN;
            return new SurfaceAddress(new Panel(pos.getX()>>4,pos.getZ()>>4,side,Math.clamp(floor,0,120)),x,z);
        }
        if(face.getAxis()==Direction.Axis.X && (x==0 || x==15)) side=x==0?Direction.WEST:Direction.EAST;
        else if(z==0 || z==15) side=z==0?Direction.NORTH:Direction.SOUTH;
        else side=x==0?Direction.WEST:Direction.EAST;
        int along=side.getAxis()==Direction.Axis.X?z:x;
        if(side==Direction.NORTH || side==Direction.EAST) along=15-along;
        return new SurfaceAddress(new Panel(pos.getX()>>4,pos.getZ()>>4,side,floor),along-1,y-floor-1);
    }
}
