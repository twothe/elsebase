package dev.elsebase.world;

/** Independent four-sided room shells; adjacent wall halves share canonical seeded openings. */
public final class RoomLayout {
    public static final int FLOOR = 64, CEILING = 72, MIN_Y = 0, HEIGHT = 128, STRIDE = 8;
    public enum Material { AIR, BEDROCK, FLOOR, BORDER, WALL, CEILING, LIGHT }
    private RoomLayout() {}

    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    /** Sparse shared edges; each cell chooses one mandatory exit. */
    public static int edge(long seed, int cellX, int cellZ, boolean west) {
        long hash = mix(seed ^ mix(cellX) ^ Long.rotateLeft(mix(cellZ), 23) ^ (west ? 71 : 131));
        int roll = (int) Math.floorMod(hash, 100);
        return roll < 10 ? 0 : roll < 40 || mandatory(seed, cellX, cellZ) == (west ? 0 : 2)
                || mandatory(seed, cellX - (west ? 1 : 0), cellZ - (west ? 0 : 1)) == (west ? 1 : 3) ? 1 : 2;
    }

    private static int mandatory(long seed, int x, int z) {
        return (int) Math.floorMod(mix(seed ^ mix(x) ^ Long.rotateLeft(mix(z), 29)), 4);
    }
    public static int floorAt(int y) { return Math.floorDiv(y, STRIDE) * STRIDE; }
    public static int ceilingAt(int floor) { return Math.min(floor + STRIDE, HEIGHT - 1); }
    public static long levelSeed(long seed, int floor) { return mix(seed ^ mix(floor + 1)); }

    /** Start of a two-block opening, kept away from corner pillars and shared by adjacent halves. */
    public static int doorOffset(long seed, int cellX, int cellZ, boolean west) {
        return 2 + (int) Math.floorMod(mix(seed ^ mix(cellX) ^ Long.rotateLeft(mix(cellZ), 23)
                ^ (west ? 0x632be59bd9b4e019L : 0x9e3779b97f4a7c15L)), 12);
    }

    public static Material material(long seed, int x, int y, int z) {
        if (y < MIN_Y || y >= HEIGHT) return Material.AIR;
        if (y == MIN_Y) return Material.BEDROCK;
        int lx = Math.floorMod(x, 16), lz = Math.floorMod(z, 16);
        if (y % STRIDE == 0 || y == HEIGHT - 1) {
            return border(lx) || border(lz) ? Material.BORDER : Material.FLOOR;
        }
        int floor = floorAt(y);
        if (border(lx) && border(lz)) return Material.WALL;
        if (!border(lx) && !border(lz)) return Material.AIR;
        seed = levelSeed(seed, floor);
        boolean west = border(lx);
        int cx = Math.floorDiv(x, 16) + (lx == 15 ? 1 : 0);
        int cz = Math.floorDiv(z, 16) + (lz == 15 ? 1 : 0);
        int type = edge(seed, cx, cz, west);
        int along = west ? lz : lx;
        int start = doorOffset(seed, cx, cz, west);
        return type == 0 || type == 1 && along >= start && along < start + 2
                && y <= floor + 2 ? Material.AIR : Material.WALL;
    }
    private static boolean border(int local) { return local == 0 || local == 15; }
}
