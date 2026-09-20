package dev.elsebase.portal;

import dev.elsebase.Elsebase;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Immutable foot position of a one-by-two cardinal doorway. */
public record Endpoint(ResourceKey<Level> dimension, BlockPos position, Direction facing) {
    public static final int WIDTH = 1, HEIGHT = 2;
    public Endpoint {
        position = position.immutable();
        if (!facing.getAxis().isHorizontal()) throw new IllegalArgumentException("Portal facing must be horizontal");
    }
    public Direction right() { return facing.getClockWise(); }
    public List<BlockPos> blocks() {
        return List.of(position, position.above());
    }
    public Vec3 center() {
        return Vec3.atBottomCenterOf(position);
    }
    public boolean inner() { return dimension.equals(Elsebase.DIMENSION); }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("dimension", dimension.location().toString());
        tag.putLong("position", position.asLong());
        tag.putString("facing", facing.getName());
        return tag;
    }
    public static Endpoint load(CompoundTag tag) {
        dev.elsebase.SavedFields.require(tag,"position",net.minecraft.nbt.Tag.TAG_LONG);
        Direction facing = Direction.byName(tag.getString("facing"));
        if (facing == null) throw new IllegalArgumentException("Missing portal facing");
        return new Endpoint(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension"))),
                BlockPos.of(tag.getLong("position")), facing);
    }
}
