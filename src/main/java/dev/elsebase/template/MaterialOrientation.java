package dev.elsebase.template;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Wall materials use the north wall as their canonical orientation; horizontal surfaces retain world axes. */
public final class MaterialOrientation {
    private static Rotation rotation(Direction wall) {
        return switch(wall) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
    public static BlockState toWorld(BlockState state,Direction wall) { return state.rotate(rotation(wall)); }
    public static BlockState toPattern(BlockState state,Direction wall) {
        var rotation=rotation(wall);
        return state.rotate(rotation==Rotation.CLOCKWISE_90?Rotation.COUNTERCLOCKWISE_90:rotation==Rotation.COUNTERCLOCKWISE_90?Rotation.CLOCKWISE_90:rotation);
    }
    private MaterialOrientation() {}
}
