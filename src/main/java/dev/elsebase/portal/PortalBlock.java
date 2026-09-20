package dev.elsebase.portal;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Two-part doorway with a non-colliding, textured interior contact trigger. */
public final class PortalBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public PortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, HALF); }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PortalSurface(pos,state); }

    /** Canonical facing and half for placement and current-state reconciliation. */
    public static BlockState stateAt(Endpoint endpoint, BlockPos pos) {
        return dev.elsebase.Content.PORTAL.get().defaultBlockState().setValue(FACING, endpoint.facing())
                .setValue(HALF, pos.getY() == endpoint.position().getY() ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
    }
    /** Matches the visible inset portal surface; body overlap is sufficient, no floor is required. */
    public static net.minecraft.world.phys.AABB trigger(Endpoint endpoint) {
        var p = endpoint.position();
        double inset=1.0/16, low=p.getY()+0.5/16, high=p.getY()+2-inset;
        return endpoint.facing().getAxis()==Direction.Axis.Z
                ? new net.minecraft.world.phys.AABB(p.getX()+inset,low,p.getZ()+0.4375,p.getX()+1-inset,high,p.getZ()+0.5625)
                : new net.minecraft.world.phys.AABB(p.getX()+0.4375,low,p.getZ()+inset,p.getX()+0.5625,high,p.getZ()+1-inset);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? Block.box(0, 0, 7, 16, 16, 9) : Block.box(7, 0, 0, 9, 16, 16);
    }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof ServerPlayer player) Portals.cross(player, pos);
    }
    private boolean matchingHalf(BlockState state,BlockState other) {
        return other.is(this) && other.getValue(HALF)!=state.getValue(HALF);
    }
    /** Like vanilla doors, deleting one half removes the other through shape propagation. */
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        Direction counterpart=state.getValue(HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN;
        return side==counterpart && !matchingHalf(state,neighbor)?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():super.updateShape(state,side,neighbor,level,pos,neighborPos);
    }
    /** Atomic WorldEdits suppress shape propagation until commit, then issue neighbor notifications. */
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos sourcePos,boolean moved) {
        var other=pos.relative(state.getValue(HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN);
        if(!level.isClientSide && sourcePos.equals(other) && !matchingHalf(state,level.getBlockState(other))) level.removeBlock(pos,false);
    }
}
