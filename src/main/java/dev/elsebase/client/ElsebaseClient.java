package dev.elsebase.client;

import dev.elsebase.Elsebase;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only native NeoForge config screen; common settings are loaded at the main menu. */
@Mod(value = Elsebase.ID, dist = Dist.CLIENT)
public final class ElsebaseClient {
    public ElsebaseClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
