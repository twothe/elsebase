package dev.elsebase;

import net.minecraft.nbt.*;

/** Strict save reads: vanilla getters otherwise turn missing/wrongly typed data into empty registries or zero coordinates. */
public final class SavedFields {
    public static void require(CompoundTag tag,String key,int type) {
        if(!tag.contains(key,type)) throw new IllegalStateException("Missing or invalid saved field: "+key);
    }
    public static ListTag rows(CompoundTag tag,String key) {
        require(tag,key,Tag.TAG_LIST);
        var list=(ListTag)tag.get(key);
        if(!list.isEmpty() && list.getElementType()!=Tag.TAG_COMPOUND) throw new IllegalStateException("Invalid saved list: "+key);
        return list;
    }
    private SavedFields() {}
}
