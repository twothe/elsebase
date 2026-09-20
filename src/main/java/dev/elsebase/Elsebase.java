package dev.elsebase;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Common entry point; no client types are loaded on a dedicated server. */
@Mod(Elsebase.ID)
public final class Elsebase {
    public static final String ID = "elsebase";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, id("backdoor"));

    public Elsebase(IEventBus bus, ModContainer container) {
        Content.register(bus);
        container.registerConfig(ModConfig.Type.COMMON, Settings.SPEC);
        bus.addListener(Network::register);
        bus.addListener(Themes::register);
        bus.addListener(IntegrationTests::register);
        NeoForge.EVENT_BUS.register(new ServerEvents());
        LOGGER.info("Elsebase initialized: persistent rooms and static portal surfaces");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
