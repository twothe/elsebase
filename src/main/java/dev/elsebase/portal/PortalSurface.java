package dev.elsebase.portal;

import dev.elsebase.Content;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Non-ticking client surface anchor. Pair ownership and travel remain in server SavedData. */
public final class PortalSurface extends BlockEntity {
    public PortalSurface(BlockPos pos, BlockState state) { super(Content.PORTAL_SURFACE.get(),pos,state); }
}
