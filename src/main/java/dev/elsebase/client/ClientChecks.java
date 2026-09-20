package dev.elsebase.client;

import dev.elsebase.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Opt-in development check: runClient -PverifyClient opens and captures the native config editor. */
@EventBusSubscriber(modid = Elsebase.ID, value = Dist.CLIENT)
public final class ClientChecks {
    private static int stage, ticks;
    private ClientChecks() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("elsebase.verifyClient") || stage == 2) return;
        var minecraft = Minecraft.getInstance();
        if (stage == 0 && minecraft.getOverlay() == null
                && minecraft.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            // The isolated development profile may not have completed first-launch onboarding.
            minecraft.setScreen(new TitleScreen());
        }
        if (stage == 0 && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
            if (!Settings.SPEC.isLoaded()) throw new IllegalStateException("Main-menu common config is not loaded");
            var container = ModList.get().getModContainerById(Elsebase.ID).orElseThrow();
            var factory = container.getCustomExtension(IConfigScreenFactory.class).orElseThrow();
            stage = 1;
            minecraft.setScreen(factory.createScreen(container,minecraft.screen));
        } else if (stage == 1 && ++ticks >= 20) {
            if (!(minecraft.screen instanceof ConfigurationScreen.ConfigurationSectionScreen))
                throw new IllegalStateException("Native config editor did not open");
            stage = 2;
            Elsebase.LOGGER.info("PASS: native config factory opens editable common configuration from the main menu");
            Screenshot.grab(minecraft.gameDirectory,"elsebase-config-check.png",minecraft.getMainRenderTarget(),
                    result -> Elsebase.LOGGER.info("Config screen capture: {}",result.getString()));
        }
    }
}
