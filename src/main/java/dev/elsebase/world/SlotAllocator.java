package dev.elsebase.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Version-one UUID/seed allocation over reserved slots; exhaustion never reuses a previous factory. */
public final class SlotAllocator {
    public record Slot(int x, int z) {}
    private final List<Slot> slots;

    public SlotAllocator(int radius, int spacing) {
        if (radius < 1 || radius > 1000000 || spacing < 256 || spacing % 16 != 0) {
            throw new IllegalArgumentException("Allocation requires radius 1..1000000 and chunk-aligned spacing >=256");
        }
        int bound = radius / spacing + 1;
        if ((long) (bound * 2 + 1) * (bound * 2 + 1) > 1000000) {
            throw new IllegalArgumentException("Allocation configuration exceeds one million candidate cells");
        }
        List<Slot> candidates = new ArrayList<>();
        for (int x = -bound; x <= bound; x++) for (int z = -bound; z <= bound; z++) {
            long px = (long) x * spacing + 8, pz = (long) z * spacing + 8;
            if (px * px + pz * pz <= (long) radius * radius) candidates.add(new Slot((int) px, (int) pz));
        }
        if (candidates.isEmpty()) throw new IllegalArgumentException("Allocation has no usable slots");
        slots = List.copyOf(candidates);
    }

    public int capacity() { return slots.size(); }

    /** Call only on the server thread, and persist the reservation before exposing its endpoint. */
    public Slot reserve(long seed, UUID player, Set<Slot> occupied) {
        long hash = RoomLayout.mix(seed ^ RoomLayout.mix(player.getMostSignificantBits())
                ^ Long.rotateLeft(RoomLayout.mix(player.getLeastSignificantBits()), 17) ^ 0x454c534542415345L);
        int start = Math.floorMod(hash, slots.size());
        for (int offset = 0; offset < slots.size(); offset++) {
            Slot candidate = slots.get((start + offset) % slots.size());
            if (!occupied.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("All Elsebase starting slots are reserved");
    }
}
