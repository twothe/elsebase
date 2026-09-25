package dev.elsebase;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Installation-wide server policy, editable before loading a world through NeoForge's config screen. */
public final class Settings {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue TEMPLATE_IMPORTS, PERSONAL_TEMPLATES;
    public static final ModConfigSpec.IntValue TEMPLATE_QUOTA;
    public static final ModConfigSpec.ConfigValue<String> TEMPLATE_DEFAULT;
    public static final ModConfigSpec.BooleanValue INSTANT, NATURAL_SPAWNS, DARKNESS;
    public static final ModConfigSpec.BooleanValue START_IN_BACKDOOR, REQUIRE_KNOWN_RETURN;
    public static final ModConfigSpec.IntValue COMBAT_LOCK_SECONDS;
    public static final ModConfigSpec.IntValue PERMANENT_LIMIT, MIRROR_LIMIT, EDIT_BUDGET, RADIUS, SPACING;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED;
    public static final ModConfigSpec.EnumValue<dev.elsebase.preview.PreviewServer.Budget> PREVIEW_BUDGET;
    static {
        var b = new ModConfigSpec.Builder();
        b.push("portals");
        INSTANT = b.comment("Disables new outside instant entrances; existing returns remain usable.").define("allowInstant", true);
        COMBAT_LOCK_SECONDS = b.comment("Seconds after an incoming attack during which instant summon/recall is blocked. Fire/poison ticks do not renew it. Existing portal travel is unaffected. Zero disables the lock.")
                .defineInRange("combatLockSeconds", 3, 0, 300);
        REQUIRE_KNOWN_RETURN = b.comment("Refuse instant summon inside the Backdoor when no outside destination is recorded. Known returns retain emergency recovery.")
                .define("requireKnownReturn", false);
        PERMANENT_LIMIT = b.comment("Set zero to disable new permanent pairs. Existing pairs remain usable.").defineInRange("maxPermanentPairsPerPlayer", 4, 0, 64);
        EXCLUDED = b.comment("Dimension IDs in which new entrances cannot be created. Existing returns remain usable.")
                .defineListAllowEmpty("excludedExternalDimensions", List.of(), () -> "minecraft:the_end",
                        v -> v instanceof String s && net.minecraft.resources.ResourceLocation.tryParse(s) != null);
        b.pop().push("chunkloading");
        MIRROR_LIMIT = b.comment("Maximum direct endpoint chunks held by Elsebase. Zero disables proactive loading; tickets may also affect neighboring chunks.")
                .defineInRange("maxMirroredEndpointChunks", 256, 0, 1024);
        b.pop().push("structure");
        EDIT_BUDGET = b.comment("Global changed-block budget per tick. Panels are committed atomically; minimum covers one ceiling.")
                .defineInRange("maxChangedBlocksPerTick", 1024, 256, 8192);
        b.pop().push("allocation");
        RADIUS = b.comment("Frozen on first allocation. Changing this for an existing save is rejected.").defineInRange("radius", 131072, 1024, 1000000);
        SPACING = b.comment("Frozen with radius; must be divisible by 16. New worlds recommended.").defineInRange("minimumSpacing", 8192, 256, 1000000);
        b.pop().push("world");
        START_IN_BACKDOOR = b.comment("New players start at their personal Backdoor anchor with a respawn point there. Existing players and subsequent logins retain their location.")
                .define("startInBackdoor", false);
        NATURAL_SPAWNS = b.comment("Allow natural mobs; spawners and machines retain normal rules.").define("allowNaturalMobSpawning", false);
        DARKNESS = b.comment("Disable uniform visual brightness in the Backdoor. No generated light sources in either mode. Player lighting and natural spawn rules remain normal. Server controls multiplayer brightness.")
                .define("darkness", false);
        b.pop().push("preview");
        PREVIEW_BUDGET = b.comment("Read-only portal snapshot budget. Uses loaded chunks only; OFF retains portal travel. LOW/BALANCED/HIGH allow 8/16/32 viewers and at most 1/2/4 section copies per tick globally.")
                .defineEnum("budget", dev.elsebase.preview.PreviewServer.Budget.BALANCED);
        b.pop().push("templates");
        TEMPLATE_IMPORTS=b.define("allowPlayerImports",true);
        PERSONAL_TEMPLATES=b.define("allowPersonalDefaults",true);
        TEMPLATE_QUOTA=b.defineInRange("templatesPerPlayer",64,1,256);
        TEMPLATE_DEFAULT=b.comment("Built-in theme name or complete theme ID. Reload templates after changes.").define("defaultStyle","quiet_workshop");
        b.pop();
        SPEC = b.build();
    }
    private Settings() {}
}
