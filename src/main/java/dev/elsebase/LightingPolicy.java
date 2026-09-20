package dev.elsebase;

import org.joml.Vector3f;

/** Visual lightmap policy only: never changes block light, spawn checks or player-provided light. */
public final class LightingPolicy {
    private static Boolean remoteDarkness;
    private LightingPolicy() {}
    public static void receive(boolean darkness) { remoteDarkness = darkness; }
    public static void disconnect() { remoteDarkness = null; }
    public static boolean darkness() { return remoteDarkness != null ? remoteDarkness : Settings.DARKNESS.get(); }
    public static void apply(boolean darkness, Vector3f color) {
        if (!darkness) color.set(1,1,1);
    }
}
