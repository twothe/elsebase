package dev.elsebase;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.*;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Registers only the isolated server-test fixture; room themes are authoritative patterns, not global packs. */
public final class Themes {
    public static void register(AddPackFindersEvent event) {
        if (Boolean.getBoolean("neoforge.gameTestServer")) event.addPackFinders(Elsebase.id("gametest_pack"), PackType.SERVER_DATA,
                Component.literal("Elsebase GameTest dimensions"), PackSource.BUILT_IN, true, Pack.Position.TOP);
    }
    private Themes() {}
}
