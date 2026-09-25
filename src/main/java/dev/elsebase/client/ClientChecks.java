package dev.elsebase.client;

import dev.elsebase.*;
import dev.elsebase.template.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Isolated real-client config regression: localization, selection, cancel, undo, reset and persistence. */
@EventBusSubscriber(modid = Elsebase.ID, value = Dist.CLIENT)
public final class ClientChecks {
    private static int stage, ticks;
    private static final long STARTED = System.currentTimeMillis();
    private ClientChecks() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("elsebase.verifyClient") || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() - STARTED > 120000) throw new IllegalStateException("Config check timeout at stage " + stage);
            require(!mc.mouseHandler.isMouseGrabbed(), "Cursor stays free");
            for (var category : net.minecraft.sounds.SoundSource.values()) require(mc.options.getSoundSourceVolume(category) == 0, "Audio muted");
            if (mc.getOverlay() != null || ++ticks < 20) return;
            ticks = 0;
            switch (stage) {
                case 0 -> {
                    if (mc.screen instanceof AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
                    if (!(mc.screen instanceof TitleScreen)) return;
                    require(Settings.SPEC.isLoaded(), "Common config loaded at title");
                    Settings.TEMPLATE_DEFAULT.set("quiet_workshop");
                    Settings.COMBAT_LOCK_SECONDS.set(3); Settings.REQUIRE_KNOWN_RETURN.set(false); Settings.START_IN_BACKDOOR.set(false);
                    Files.createDirectories(TemplateClient.library());
                    Files.write(TemplateClient.library().resolve("config_fixture.json"), BuiltinThemes.create("quiet_workshop", "Local Fixture").bytes());
                    mc.getLanguageManager().setSelected("de_de"); mc.options.languageCode = "de_de";
                    stage = 1; mc.reloadResourcePacks();
                }
                case 1 -> { require(UiText.text("biome.elsebase.backdoor").equals("Backdoor-Werkraum"), "German biome name"); open(mc); stage = 2; }
                case 2 -> { press(mc, net.minecraft.client.resources.language.I18n.get("neoforge.configuration.uitext.type.common", "Elsebase")); stage = 20; }
                case 20 -> { press(mc, UiText.text("elsebase.configuration.portals")); stage = 21; }
                case 21 -> {
                    require(descendants(mc.screen).stream().filter(StringWidget.class::isInstance).map(StringWidget.class::cast)
                            .anyMatch(w -> w.getMessage().getString().equals(UiText.text("elsebase.configuration.combatLockSeconds"))), "Combat delay has a translated native label");
                    toggle(mc, "elsebase.configuration.requireKnownReturn"); require(Settings.REQUIRE_KNOWN_RETURN.get(), "Native return restriction switch applies");
                    capture(mc, "config-portals-de.png"); mc.screen.onClose(); stage = 22;
                }
                case 22 -> { press(mc, UiText.text("elsebase.configuration.world")); stage = 23; }
                case 23 -> {
                    toggle(mc, "elsebase.configuration.startInBackdoor"); require(Settings.START_IN_BACKDOOR.get(), "Native Backdoor start switch applies");
                    capture(mc, "config-start-de.png"); mc.screen.onClose(); mc.screen.onClose();
                    var saved = Files.readString(mc.gameDirectory.toPath().resolve("config/elsebase-common.toml"));
                    require(saved.contains("requireKnownReturn = true") && saved.contains("startInBackdoor = true") && saved.contains("combatLockSeconds = 3"), "Gameplay policy persisted in TOML");
                    Settings.REQUIRE_KNOWN_RETURN.set(false); Settings.START_IN_BACKDOOR.set(false); Settings.SPEC.save();
                    open(mc); stage = 24;
                }
                case 24 -> { press(mc, net.minecraft.client.resources.language.I18n.get("neoforge.configuration.uitext.type.common", "Elsebase")); stage = 3; }
                case 3 -> { press(mc, UiText.text("elsebase.configuration.templates")); stage = 4; }
                case 4 -> { press(mc, UiText.theme("quiet_workshop", "")); stage = 5; }
                case 5 -> {
                    require(mc.screen instanceof ConfigThemeScreen, "Custom picker opened");
                    capture(mc, "config-theme-de.png");
                    var search = descendants(mc.screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                    search.setValue("Arkan"); press(mc, UiText.theme("arcane_archive", "")); press(mc, UiText.text("elsebase.ui.select"));
                    require(Settings.TEMPLATE_DEFAULT.get().equals("arcane_archive"), "Selection changes raw config ID"); stage = 6;
                }
                case 6 -> { press(mc, ConfigurationScreen.UNDO.getString()); require(Settings.TEMPLATE_DEFAULT.get().equals("quiet_workshop"), "Native undo restores original ID"); press(mc, UiText.theme("quiet_workshop", "")); stage = 7; }
                case 7 -> { descendants(mc.screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue(UiText.theme("deepstone_halls", "")); press(mc, UiText.theme("deepstone_halls", "")); press(mc, UiText.text("elsebase.ui.cancel")); require(Settings.TEMPLATE_DEFAULT.get().equals("quiet_workshop"), "Cancel preserves original"); press(mc, UiText.theme("quiet_workshop", "")); stage = 8; }
                case 8 -> {
                    var search = descendants(mc.screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow(); search.setValue("Local Fixture");
                    press(mc, "Local Fixture"); press(mc, UiText.text("elsebase.ui.select")); require(Settings.TEMPLATE_DEFAULT.get().equals("pack/config_fixture"), "Local file uses server library identity"); stage = 9;
                }
                case 9 -> {
                    press(mc, ConfigurationScreen.RESET.getString()); require(Settings.TEMPLATE_DEFAULT.get().equals("quiet_workshop"), "Native reset restores default");
                    press(mc, UiText.theme("quiet_workshop", "")); stage = 10;
                }
                case 10 -> { press(mc, UiText.theme("arcane_archive", "")); press(mc, UiText.text("elsebase.ui.select")); mc.screen.onClose(); mc.screen.onClose();
                    require(Files.readString(mc.gameDirectory.toPath().resolve("config/elsebase-common.toml")).contains("defaultStyle = \"arcane_archive\""), "Saved file contains selected ID");
                    mc.getLanguageManager().setSelected("zh_cn"); mc.options.languageCode = "zh_cn"; stage = 11; mc.reloadResourcePacks(); }
                case 11 -> { require(UiText.text("biome.elsebase.backdoor").equals("Backdoor 工作间"), "Chinese biome name"); mc.setScreen(new ConfigThemeScreen(new TitleScreen(), "arcane_archive", value -> {})); stage = 12; }
                case 12 -> {
                    capture(mc, "config-theme-zh.png"); require(UiText.themeSearch("奥术").contains("builtin/arcane_archive"), "Translated search resolves stable builtin ID");
                    Settings.TEMPLATE_DEFAULT.set("quiet_workshop"); Settings.SPEC.save(); Files.deleteIfExists(TemplateClient.library().resolve("config_fixture.json"));
                    Files.writeString(mc.gameDirectory.toPath().resolve("config-check-result.json"), "{\"status\":\"passed\",\"started\":" + STARTED + "}");
                    Elsebase.LOGGER.info("PASS: localized config theme picker, search, local library, cancel, undo/reset, save, German/Chinese resources, muted audio and free cursor"); stage = 99; mc.stop();
                }
            }
        } catch (Exception error) { stage = 99; Elsebase.LOGGER.error("Config client regression failed", error); mc.stop(); }
    }
    private static void open(Minecraft mc) {
        var mod = ModList.get().getModContainerById(Elsebase.ID).orElseThrow();
        mc.setScreen(mod.getCustomExtension(IConfigScreenFactory.class).orElseThrow().createScreen(mod, new TitleScreen()));
    }
    private static List<GuiEventListener> descendants(ContainerEventHandler parent) {
        List<GuiEventListener> result = new ArrayList<>();
        for (var child : parent.children()) { result.add(child); if (child instanceof ContainerEventHandler container) result.addAll(descendants(container)); }
        return result;
    }
    private static void press(Minecraft mc, String label) {
        var buttons = descendants(mc.screen).stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
        buttons.stream().filter(b -> b.active && b.getMessage().getString().contains(label)).findFirst().orElseThrow(() -> new IllegalStateException("Missing button " + label + ": " + buttons.stream().map(b -> b.getMessage().getString()).toList())).onPress();
    }
    private static void capture(Minecraft mc, String name) { Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), result -> {}); }
    private static void toggle(Minecraft mc, String key) {
        for (var child : descendants(mc.screen)) if (child instanceof ContainerEventHandler row
                && row.children().stream().anyMatch(w -> w instanceof StringWidget label && label.getMessage().getString().equals(UiText.text(key)))) {
            row.children().stream().filter(CycleButton.class::isInstance).map(CycleButton.class::cast).findFirst().orElseThrow().onPress();
            return;
        }
        throw new IllegalStateException("Missing config switch: " + key);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
