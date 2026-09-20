package dev.elsebase;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.*;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Optional global resource-pack overlays; geometry and gameplay are shared by every theme. */
public final class Themes {
    public static final String[] OPTIONAL = {"arcane_archive", "verdant_cloister", "astral_observatory",
            "deepstone_halls", "porcelain_sanctuary", "service_layer"};
    public static void register(AddPackFindersEvent event) {
        if (Boolean.getBoolean("neoforge.gameTestServer")) event.addPackFinders(Elsebase.id("gametest_pack"), PackType.SERVER_DATA,
                Component.literal("Elsebase GameTest dimensions"), PackSource.BUILT_IN, true, Pack.Position.TOP);
        for (String name : OPTIONAL) event.addPackFinders(Elsebase.id("resourcepacks/" + name), PackType.CLIENT_RESOURCES,
                Component.literal("Elsebase: " + name.replace('_', ' ')), PackSource.BUILT_IN, false, Pack.Position.TOP);
    }
    private Themes() {}
}
