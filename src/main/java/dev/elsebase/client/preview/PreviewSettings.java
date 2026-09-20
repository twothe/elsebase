package dev.elsebase.client.preview;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Small client presentation presets, independent of server travel and snapshot budgets. */
public final class PreviewSettings {
    public enum Quality { OFF, LOW, BALANCED, HIGH }
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Quality> QUALITY;
    public static final ModConfigSpec.BooleanValue TRANSITION;
    static {
        var b=new ModConfigSpec.Builder(); b.push("render");
        QUALITY=b.comment("Bounded block previews. Active Iris/Oculus shader packs automatically use the animated fallback. All presets show at most one live portal; LOW/BALANCED/HIGH use up to 256x512, 512x1024, 768x1536 pixels.").defineEnum("previewQuality",Quality.BALANCED);
        TRANSITION=b.comment("Replace loading screens only for Elsebase doorway travel. Other dimension changes remain unchanged.").define("immersivePortalTransition",true);
        b.pop(); SPEC=b.build();
    }
    private PreviewSettings() {}
}
