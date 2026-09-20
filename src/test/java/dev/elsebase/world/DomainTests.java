package dev.elsebase.world;

import java.util.*;

/** Executable dependency-free behavior tests for the actual allocator and room layout. */
public final class DomainTests {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        var allocator = new SlotAllocator(131072, 8192);
        require(allocator.capacity() == 795, "Expected exact disk capacity");
        Set<SlotAllocator.Slot> reserved = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            UUID id = new UUID(i * 131L, i);
            var slot = allocator.reserve(71, id, reserved);
            require(slot.equals(allocator.reserve(71, id, reserved)), "Deterministic candidate");
            for (var previous : reserved) {
                long dx = slot.x() - previous.x(), dz = slot.z() - previous.z();
                require(dx*dx + dz*dz >= 8192L*8192, "Minimum separation");
            }
            reserved.add(slot);
        }
        UUID collision = new UUID(7, 9);
        var first = allocator.reserve(44, collision, Set.of());
        var second = allocator.reserve(44, collision, Set.of(first));
        require(!first.equals(second), "Forced preferred-slot collision must resolve");
        Set<SlotAllocator.Slot> full = new HashSet<>();
        for (int i = 0; i < allocator.capacity(); i++) full.add(allocator.reserve(44, collision, full));
        try { allocator.reserve(44, collision, full); throw new AssertionError("Exhaustion must fail"); }
        catch (IllegalStateException expected) { }
        try { new SlotAllocator(1000, 257); throw new AssertionError("Unaligned spacing accepted"); }
        catch (IllegalArgumentException expected) { }
        require(!first.equals(allocator.reserve(9999, collision, Set.of())), "Seed affects selection");

        Set<Integer> offsets = new HashSet<>();
        int doors = 0, sealed = 0, deadEnds = 0;
        long layoutSeed = RoomLayout.levelSeed(42,64);
        for (int cx = -32; cx < 32; cx++) for (int cz = -32; cz < 32; cz++) {
            int exits = 0;
            for (int edge : new int[]{RoomLayout.edge(layoutSeed,cx,cz,true), RoomLayout.edge(layoutSeed,cx+1,cz,true),
                    RoomLayout.edge(layoutSeed,cx,cz,false), RoomLayout.edge(layoutSeed,cx,cz+1,false)}) if (edge != 2) exits++;
            if (exits == 0) sealed++;
            if (exits == 1) deadEnds++;
            for (boolean west : new boolean[]{true,false}) {
                if (RoomLayout.edge(layoutSeed,cx,cz,west) != 1) continue;
                doors++;
                int offset = RoomLayout.doorOffset(layoutSeed,cx,cz,west);
                offsets.add(offset);
                require(offset >= 2 && offset <= 13, "Door offset leaves corner clearance");
                int air = 0;
                for (int along = 1; along < 15; along++) for (int y = 65; y < 72; y++) {
                    int x = cx*16 + (west ? 0 : along), z = cz*16 + (west ? along : 0);
                    boolean opening = RoomLayout.material(42,x,y,z) == RoomLayout.Material.AIR;
                    require(opening == (along >= offset && along < offset + 2 && y <= 66), "Door is exactly two wide, two high");
                    if (opening) air++;
                }
                require(air == 4, "Exactly four air blocks per wall half");
            }
        }
        require(offsets.size() == 12, "Door positions vary across the full intended range");
        require(doors > 2900 && doors < 6000, "Mandatory exits add doors without opening every wall");
        require(sealed == 0 && deadEnds > 0, "No sealed rooms; dead ends remain possible");
        for (int x = -17; x <= 17; x++) for (int z = -17; z <= 17; z++) {
            require(RoomLayout.material(42,x,0,z)==RoomLayout.Material.BEDROCK,"Continuous bedrock bottom");
            require(RoomLayout.material(42,x,0,z)==RoomLayout.Material.BEDROCK,"Darkness retains bedrock");
            require(RoomLayout.material(42,x,-1,z)==RoomLayout.Material.AIR,"Nothing generated below dimension");

        }
        require(RoomLayout.material(42, -8, 65, -8)==RoomLayout.Material.AIR,"Clear negative-coordinate interior");
        require(RoomLayout.material(42,2,64,2)==RoomLayout.Material.FLOOR,"Normal floor has no lamp");
        require(RoomLayout.material(42,2,64,2)==RoomLayout.Material.FLOOR,"Dark floor has no lamp");
        require(RoomLayout.material(42,2,72,2)==RoomLayout.Material.FLOOR,"Dark ceiling has no lamp");
        require(RoomLayout.material(42,2,73,2)==RoomLayout.Material.AIR,"Next room above shared ceiling");
        for (int floor=0; floor<128; floor+=8) for (int cx=-10;cx<=10;cx++) for (int cz=-10;cz<=10;cz++) {
            long seed = RoomLayout.levelSeed(123,floor);
            require(RoomLayout.edge(seed,cx,cz,true)!=2 || RoomLayout.edge(seed,cx+1,cz,true)!=2
                    || RoomLayout.edge(seed,cx,cz,false)!=2 || RoomLayout.edge(seed,cx,cz+1,false)!=2,"Every level has an exit per cell");
            require(RoomLayout.material(123,cx*16+8,floor+1,cz*16+8)==RoomLayout.Material.AIR,"Every level has clear interior");
            require(RoomLayout.material(123,cx*16+8,RoomLayout.ceilingAt(floor),cz*16+8)!=RoomLayout.Material.AIR,"Every level has ceiling");
            boolean opening = false;
            for (int offset=1;offset<16;offset++) for (int[] boundary : new int[][]{
                    {cx*16,cz*16+offset},{(cx+1)*16,cz*16+offset},
                    {cx*16+offset,cz*16},{cx*16+offset,(cz+1)*16}}) {
                if (RoomLayout.material(123,boundary[0],floor+1,boundary[1])==RoomLayout.Material.AIR
                        && RoomLayout.material(123,boundary[0],floor+2,boundary[1])==RoomLayout.Material.AIR) opening = true;
            }
            require(opening,"Generated blocks provide a usable exit on every level");
        }
        require(RoomLayout.material(42,8,128,8)==RoomLayout.Material.AIR,"Generation stops at height 128");
        for (int floor=0;floor<128;floor+=8) for (int cx=-8;cx<=8;cx++) for (int cz=-8;cz<=8;cz++) {
            for (int along=1;along<15;along++) for (int y=floor+1;y<RoomLayout.ceilingAt(floor);y++) {
                require(RoomLayout.material(42,cx*16-1,y,cz*16+along)==RoomLayout.material(42,cx*16,y,cz*16+along),"East/west wall halves align exactly");
                require(RoomLayout.material(42,cx*16+along,y,cz*16-1)==RoomLayout.material(42,cx*16+along,y,cz*16),"North/south wall halves align exactly");
            }
            if (floor==0) continue;
            for (int along=0;along<16;along++) for (int[] border : new int[][]{
                    {cx*16,cz*16+along},{cx*16+15,cz*16+along},{cx*16+along,cz*16},{cx*16+along,cz*16+15}})
                require(RoomLayout.material(42,border[0],floor,border[1])==RoomLayout.Material.BORDER,"Four independent floor borders");
        }
        long started = System.nanoTime(), solid = 0;
        for (int cx=0;cx<8;cx++) for (int cz=0;cz<8;cz++)
            for (int x=0;x<16;x++) for (int z=0;z<16;z++) for (int y=0;y<128;y++)
                if (RoomLayout.material(42,cx*16+x,y,cz*16+z)!=RoomLayout.Material.AIR) solid++;
        System.out.printf(java.util.Locale.ROOT,"Layout sample: 64 columns, %.1f solid blocks/column, %.1f ms (geometry only, no lighting/I/O).%n",solid/64.0,(System.nanoTime()-started)/1_000_000.0);
        System.out.println("PASS: allocation capacity, 100-player spacing, collision, exhaustion, seed, negative coordinates, varied door geometry, guaranteed exits, bedrock and lighting.");
    }
}
