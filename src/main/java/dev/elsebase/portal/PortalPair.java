package dev.elsebase.portal;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Every persistent pair has exactly one inner endpoint and an immutable accounting owner. */
public record PortalPair(UUID id, UUID owner, boolean permanent, Endpoint inner, Endpoint external) {
    public PortalPair {
        if (!inner.inner() || external.inner()) throw new IllegalArgumentException("Portal topology requires exactly one Backdoor endpoint");
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("id", id); tag.putUUID("owner", owner); tag.putBoolean("permanent", permanent);
        tag.put("inner", inner.save()); tag.put("external", external.save());
        return tag;
    }
    public static PortalPair load(CompoundTag tag) {
        dev.elsebase.SavedFields.require(tag,"permanent",net.minecraft.nbt.Tag.TAG_BYTE);
        return new PortalPair(tag.getUUID("id"), tag.getUUID("owner"), tag.getBoolean("permanent"),
                Endpoint.load(tag.getCompound("inner")), Endpoint.load(tag.getCompound("external")));
    }
}
