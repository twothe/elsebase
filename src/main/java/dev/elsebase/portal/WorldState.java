package dev.elsebase.portal;

import dev.elsebase.*;
import dev.elsebase.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-thread persistent reservations, references and portal registry; saves retain offline ownership. */
public final class WorldState extends SavedData {
    public record Home(SlotAllocator.Slot slot, Endpoint reference, BlockPos anchor) {}
    public final Map<UUID, Home> homes = new LinkedHashMap<>();
    public final Map<UUID, PortalPair> pairs = new LinkedHashMap<>();
    public final Map<UUID, InstantExpiry.State> lifetimes = new LinkedHashMap<>();
    public final Map<UUID, Endpoint> returns = new LinkedHashMap<>();
    private final Map<EndpointBlock, UUID> index = new HashMap<>();
    private record EndpointBlock(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos position) {}
    private int radius, spacing;
    private SlotAllocator allocator;
    /** Frozen grid spacing, also used for stable template regions before a player is online. */
    public int allocationSpacing() { return spacing==0?Settings.SPACING.get():spacing; }

    public static WorldState get(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        var factory = new Factory<>(WorldState::new, WorldState::load);
        WorldState loaded = storage.get(factory, "elsebase");
        if (loaded != null) return loaded;
        // Vanilla swallows deserialization failures and normally creates new data. Never lose reservations that way.
        if (java.nio.file.Files.exists(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/elsebase.dat")))
            throw new IllegalStateException("Elsebase saved data could not be loaded. For an incompatible development build, create a new test world; otherwise restore a compatible backup.");
        var created = new WorldState();
        storage.set("elsebase", created);
        return created;
    }
    public Home home(MinecraftServer server, UUID player) {
        Home existing = homes.get(player);
        if (existing != null) return existing;
        if (allocator == null) {
            if (radius == 0) { radius = Settings.RADIUS.get(); spacing = Settings.SPACING.get(); }
            if (radius != Settings.RADIUS.get() || spacing != Settings.SPACING.get())
                throw new IllegalStateException("Allocation settings differ from this world's frozen radius/spacing");
            allocator = new SlotAllocator(radius, spacing);
        }
        if (radius != Settings.RADIUS.get() || spacing != Settings.SPACING.get())
            throw new IllegalStateException("Allocation settings differ from this world's frozen radius/spacing");
        Set<SlotAllocator.Slot> occupied = new HashSet<>();
        homes.values().forEach(h -> occupied.add(h.slot()));
        var slot = allocator.reserve(server.overworld().getSeed(), player, occupied);
        Home home = new Home(slot, new Endpoint(Elsebase.DIMENSION, new BlockPos(slot.x(), RoomLayout.FLOOR + 1, slot.z()), Direction.SOUTH), new BlockPos(slot.x(), RoomLayout.FLOOR + 1, slot.z() + 1));
        homes.put(player, home); setDirty();
        return home;
    }
    public PortalPair instant(UUID owner) {
        return pairs.values().stream().filter(p -> !p.permanent() && p.owner().equals(owner)).findFirst().orElse(null);
    }
    public PortalPair at(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, BlockPos pos) {
        return pairs.get(index.get(new EndpointBlock(dim, pos)));
    }
    public void rememberReturn(UUID owner, Endpoint endpoint) {
        if (endpoint.inner()) throw new IllegalArgumentException("Return must be outside the Backdoor");
        returns.put(owner,endpoint); setDirty();
    }
    public void validatePut(PortalPair pair) {
        var candidate = new LinkedHashMap<>(pairs); candidate.put(pair.id(),pair);
        buildIndex(candidate.values());
    }
    public void put(PortalPair pair) {
        var candidate = new LinkedHashMap<>(pairs);
        candidate.put(pair.id(), pair);
        var nextIndex = buildIndex(candidate.values());
        pairs.put(pair.id(), pair);
        index.clear(); index.putAll(nextIndex); setDirty();
    }
    public void remove(UUID id) { pairs.remove(id); lifetimes.remove(id); rebuild(); setDirty(); }
    private void rebuild() {
        var nextIndex = buildIndex(pairs.values());
        index.clear(); index.putAll(nextIndex);
    }
    private static Map<EndpointBlock, UUID> buildIndex(Collection<PortalPair> candidates) {
        Map<EndpointBlock, UUID> nextIndex = new HashMap<>();
        Set<UUID> instantOwners = new HashSet<>();
        for (PortalPair pair : candidates) {
            if (!pair.permanent() && !instantOwners.add(pair.owner())) throw new IllegalStateException("Duplicate instant portal owner");
            for (Endpoint e : List.of(pair.inner(), pair.external())) for (BlockPos pos : e.blocks()) {
                UUID old = nextIndex.put(new EndpointBlock(e.dimension(), pos), pair.id());
                if (old != null) throw new IllegalStateException("Overlapping saved portal endpoints");
            }
        }
        return nextIndex;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 4); tag.putInt("radius", radius); tag.putInt("spacing", spacing);
        ListTag homeTags = new ListTag();
        homes.forEach((id, home) -> {
            CompoundTag h = new CompoundTag(); h.putUUID("owner", id);
            h.putInt("slotX", home.slot().x()); h.putInt("slotZ", home.slot().z()); h.put("reference", home.reference().save());
            if (home.anchor() != null) h.putLong("anchor", home.anchor().asLong());
            homeTags.add(h);
        });
        tag.put("homes", homeTags);
        ListTag returnTags = new ListTag();
        returns.forEach((owner,endpoint) -> { var saved = endpoint.save(); saved.putUUID("owner",owner); returnTags.add(saved); });
        tag.put("returns",returnTags);
        ListTag lifetimeTags = new ListTag();
        lifetimes.forEach((id,state) -> {
            var saved = new CompoundTag(); saved.putUUID("pair",id); saved.putLong("expires",state.expires());
            saved.putBoolean("inside",state.inside()); lifetimeTags.add(saved);
        });
        tag.put("lifetimes",lifetimeTags);
        ListTag portalTags = new ListTag(); pairs.values().forEach(p -> portalTags.add(p.save())); tag.put("portals", portalTags);
        return tag;
    }
    public static WorldState load(CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.getInt("version");
        if (version != 4) throw new IllegalStateException("Unsupported Elsebase save version; create a new test world for this development build");
        SavedFields.require(tag,"radius",Tag.TAG_INT); SavedFields.require(tag,"spacing",Tag.TAG_INT);
        var data = new WorldState(); data.radius = tag.getInt("radius"); data.spacing = tag.getInt("spacing");
        for (Tag value : SavedFields.rows(tag,"homes")) {
            CompoundTag h = (CompoundTag) value;
            SavedFields.require(h,"slotX",Tag.TAG_INT); SavedFields.require(h,"slotZ",Tag.TAG_INT);
            Endpoint ref = Endpoint.load(h.getCompound("reference"));
            if (!ref.inner()) throw new IllegalStateException("Home outside Backdoor");
            if (!h.contains("anchor", Tag.TAG_LONG)) throw new IllegalStateException("Missing reference anchor");
            var home = new Home(new SlotAllocator.Slot(h.getInt("slotX"), h.getInt("slotZ")), ref,
                    BlockPos.of(h.getLong("anchor")));
            if (data.homes.put(h.getUUID("owner"), home) != null) throw new IllegalStateException("Duplicate saved home");
        }
        for (Tag value : SavedFields.rows(tag,"portals")) {
            PortalPair pair = PortalPair.load((CompoundTag) value);
            if (data.pairs.put(pair.id(), pair) != null) throw new IllegalStateException("Duplicate saved portal");
        }
        for (Tag value : SavedFields.rows(tag,"returns")) {
            CompoundTag saved = (CompoundTag)value;
            Endpoint endpoint = Endpoint.load(saved);
            if (endpoint.inner() || data.returns.put(saved.getUUID("owner"),endpoint)!=null)
                throw new IllegalStateException("Invalid saved return destination");
        }
        for (Tag value : SavedFields.rows(tag,"lifetimes")) {
            var saved = (CompoundTag)value;
            SavedFields.require(saved,"expires",Tag.TAG_LONG); SavedFields.require(saved,"inside",Tag.TAG_BYTE);
            UUID id = saved.getUUID("pair");
            var pair = data.pairs.get(id);
            if (pair==null || pair.permanent() || saved.getLong("expires")<0
                    || data.lifetimes.put(id,new InstantExpiry.State(saved.getLong("expires"),saved.getBoolean("inside")))!=null)
                throw new IllegalStateException("Invalid instant portal lifetime");
        }
        if(data.radius==0 && data.spacing==0) {
            if(!data.homes.isEmpty()) throw new IllegalStateException("Reserved homes lack their allocation grid");
        } else {
            new SlotAllocator(data.radius,data.spacing);
            for(Home home:data.homes.values()) {
                long x=home.slot().x(),z=home.slot().z();
                if(Math.floorMod(x-8,data.spacing)!=0 || Math.floorMod(z-8,data.spacing)!=0 || x*x+z*z>(long)data.radius*data.radius)
                    throw new IllegalStateException("Saved reservation is outside its allocation grid");
            }
        }
        Set<SlotAllocator.Slot> reserved = new HashSet<>();
        for (Home h : data.homes.values()) if (!reserved.add(h.slot())) throw new IllegalStateException("Duplicate reserved slot");
        data.rebuild();
        return data;
    }
}
