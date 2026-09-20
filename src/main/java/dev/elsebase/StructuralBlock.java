package dev.elsebase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;

/** Provenance is explicit: generated/restored blocks are structural; normal block items are not. */
public final class StructuralBlock extends Block {
    public static final BooleanProperty STRUCTURAL = BooleanProperty.create("structural");
    public StructuralBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STRUCTURAL, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(STRUCTURAL); }
    public static boolean protectedStructure(Level level, BlockState state) {
        return level.dimension().equals(Elsebase.DIMENSION) && state.getBlock() instanceof StructuralBlock && state.getValue(STRUCTURAL);
    }
    @Override public PushReaction getPistonPushReaction(BlockState state) {
        return state.getValue(STRUCTURAL) ? PushReaction.BLOCK : PushReaction.NORMAL;
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        if (level instanceof Level world && protectedStructure(world, state) && !(entity instanceof Player)) return false;
        return super.canEntityDestroy(state, level, pos, entity);
    }
}
