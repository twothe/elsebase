package dev.elsebase.client;

import dev.elsebase.Elsebase;
import dev.elsebase.template.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;

/** Config-only selector. Commits through NeoForge's original editor to preserve undo/reset/save semantics. */
public final class ConfigThemeScreen extends Screen {
    private record Choice(String id, String name) {}
    private static final Map<String, String> LIBRARY_NAMES = new HashMap<>();
    private final Screen parent;
    private final Consumer<String> accept;
    private final List<Choice> choices = new ArrayList<>();
    private String query = "", selected, status = "";
    private int offset;
    public ConfigThemeScreen(Screen parent, String selected, Consumer<String> accept) {
        super(Component.translatable("elsebase.configuration.defaultStyle"));
        this.parent = parent; this.selected = selected; this.accept = accept;
        for (String theme : TemplateState.THEMES) choices.add(new Choice(theme, UiText.theme(theme, theme)));
    }
    public static ConfigurationSectionScreen.Element filter(ConfigurationSectionScreen.Context context, String key, ConfigurationSectionScreen.Element original) {
        if (!context.keylist().equals(List.of("templates")) || !key.equals("defaultStyle") || original == null || !(original.widget() instanceof EditBox editor)) return original;
        Button button = Button.builder(Component.literal(displayName(editor.getValue())), clicked -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            client.setScreen(new ConfigThemeScreen(client.screen, editor.getValue(), value -> {
                editor.setValue(value);
                clicked.setMessage(Component.literal(displayName(value)));
            }));
        }).width(Button.DEFAULT_WIDTH).build();
        button.setTooltip(Tooltip.create(original.tooltip()));
        return new ConfigurationSectionScreen.Element(original.name(), original.tooltip(), button, original.undoable());
    }
    private static String displayName(String id) { return UiText.theme(id, LIBRARY_NAMES.getOrDefault(id, UiText.text("elsebase.ui.select"))); }
    @Override public void added() {
        // Screen.added runs before init assigns Screen.minecraft. Always enqueue completion after initialization.
        var client = net.minecraft.client.Minecraft.getInstance();
        status = UiText.text("elsebase.ui.reading_personal_library");
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            List<Choice> local = new ArrayList<>();
            Path directory = TemplateClient.library();
            if (!Files.isDirectory(directory)) return local;
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().limit(4096).toList()) {
                    try {
                        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) continue;
                        String id = TemplateFiles.id(file);
                        local.add(new Choice(id, Theme.readFile(file).name()));
                    } catch (java.io.IOException | Pattern.Rejected invalid) {
                        Elsebase.LOGGER.debug("Config theme file rejected: {}", file.getFileName(), invalid);
                    }
                }
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            return local;
        }).whenComplete((local, error) -> client.tell(() -> {
            if (client.screen != this) return;
            if (error == null) { LIBRARY_NAMES.clear(); local.forEach(c -> LIBRARY_NAMES.put(c.id, c.name)); choices.addAll(local); status = UiText.text("elsebase.config.theme_help"); }
            else { Elsebase.LOGGER.warn("Cannot list configuration themes", error); status = UiText.text("elsebase.ui.cannot_read_personal_library_check_folder_permissions"); }
            rebuildWidgets();
        }));
    }
    private List<Choice> filtered() { return choices.stream().filter(c -> c.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList(); }
    @Override protected void init() {
        int left = width / 2 - 150;
        EditBox search = addRenderableWidget(new EditBox(font, left, 35, 300, 20, Component.translatable("elsebase.ui.search_themes")));
        search.setHint(Component.translatable("elsebase.ui.search_themes")); search.setValue(query);
        search.setResponder(value -> { query = value; offset = 0; rebuildWidgets(); });
        setInitialFocus(search);
        var rows = filtered(); int count = Math.max(1, (height - 145) / 24);
        offset = Math.min(offset, Math.max(0, rows.size() - 1));
        for (int index = offset; index < Math.min(rows.size(), offset + count); index++) {
            Choice choice = rows.get(index);
            Button button = Button.builder(Component.literal((sameId(choice.id, selected) ? "> " : "") + choice.name), b -> { selected = choice.id; rebuildWidgets(); }).bounds(left, 64 + (index - offset) * 24, 300, 20).build();
            button.setTooltip(Tooltip.create(Component.literal(choice.id))); addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { offset = Math.max(0, offset - count); rebuildWidgets(); }).bounds(left, height - 76, 35, 20).build()).active = offset > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { offset += count; rebuildWidgets(); }).bounds(left + 265, height - 76, 35, 20).build()).active = offset + count < rows.size();
        addRenderableWidget(Button.builder(Component.translatable("elsebase.ui.select"), b -> { accept.accept(selected); onClose(); }).bounds(left, height - 28, 145, 20).build()).active = choices.stream().anyMatch(c -> sameId(c.id, selected));
        addRenderableWidget(Button.builder(Component.translatable("elsebase.ui.cancel"), b -> onClose()).bounds(left + 155, height - 28, 145, 20).build());
    }
    private static boolean sameId(String first, String second) { return first.equals(second) || ("builtin/" + first).equals(second); }
    @Override public void render(GuiGraphics graphics, int x, int y, float delta) {
        graphics.fill(0, 0, width, height, 0xff101923); super.render(graphics, x, y, delta);
        graphics.drawCenteredString(font, title, width / 2, 13, 0xffffff);
        graphics.drawWordWrap(font, Component.literal(status), width / 2 - 150, height - 53, 300, 0xc8ced6);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
