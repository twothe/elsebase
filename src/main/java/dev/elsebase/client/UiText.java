package dev.elsebase.client;

import java.util.Arrays;
import dev.elsebase.template.TemplateState;
import net.minecraft.client.resources.language.I18n;

/** Client display text only: persisted theme IDs and player-authored names are never translated. */
public final class UiText {
    private UiText() {}
    public static String text(String key, Object... arguments) { return I18n.get(key, arguments); }
    public static String theme(String id, String fallback) {
        String name = id.startsWith("builtin/") ? id.substring(8) : id;
        return Arrays.asList(TemplateState.THEMES).contains(name) ? text("elsebase.theme." + name) : fallback;
    }
    /** Include matching built-in IDs so server-side pagination also works for localized searches. */
    public static String themeSearch(String query) {
        StringBuilder result = new StringBuilder(query.replace("\u001f", ""));
        for (String id : TemplateState.THEMES) if (theme(id, id).toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT)))
            result.append('\u001f').append(TemplateState.builtin(id));
        return result.toString();
    }
}
