package dev.elsebase.template;

import java.nio.file.Path;

/** Shared library filename identity; display names never determine server IDs or filesystem paths. */
public final class TemplateFiles {
    private TemplateFiles() {}
    public static String id(Path path) {
        String stem = path.getFileName().toString().replaceFirst("\\.json$", "");
        if (stem.matches("player_[0-9a-f-]{36}_[0-9a-f-]{36}")) return "player/" + stem.substring(7, 43) + "/" + stem.substring(44);
        if (!stem.matches("[a-z0-9_-]{1,80}")) throw new Pattern.Rejected("Use simple ASCII library filenames");
        return "pack/" + stem;
    }
}
