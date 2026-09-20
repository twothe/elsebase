package dev.elsebase.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.elsebase.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client-only controls and a skyless dimension; static portal models use the ordinary block renderer. */
@EventBusSubscriber(modid = Elsebase.ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final KeyMapping PORTAL = new KeyMapping("key.elsebase.portal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.elsebase");
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(PORTAL); }
    @SubscribeEvent public static void effects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Elsebase.id("backdoor"), new DimensionSpecialEffects(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, false) {
            @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float light) { return color.scale(0.15); }
            @Override public boolean isFoggyAt(int x, int y) { return false; }
            @Override public void adjustLightmapColors(net.minecraft.client.multiplayer.ClientLevel level, float partialTicks,
                    float skyDarken, float flicker, float skyLight, int pixelX, int pixelY, org.joml.Vector3f colors) {
                LightingPolicy.apply(LightingPolicy.darkness(), colors);
            }
        });
    }
    @EventBusSubscriber(modid = Elsebase.ID, value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { LightingPolicy.disconnect(); }
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) return;
            while (PORTAL.consumeClick()) PacketDistributor.sendToServer(new Network.Action());
        }
    }
    private ClientEvents() {}
}
