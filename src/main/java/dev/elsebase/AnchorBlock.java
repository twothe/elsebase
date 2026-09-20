package dev.elsebase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Thin selectable marker with no collision and no ticking block entity. */
public final class AnchorBlock extends Block {
    public AnchorBlock(Properties properties) { super(properties); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(1, 0, 1, 15, 0.5, 15);
    }
}
