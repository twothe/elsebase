package dev.elsebase.client.preview;

import dev.elsebase.Elsebase;
import dev.elsebase.preview.PreviewProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Two-screen dimension handover scoped to a server-authorized Elsebase passage; readiness remains vanilla-owned. */
@EventBusSubscriber(modid=Elsebase.ID,value=Dist.CLIENT)
public final class PortalTransition {
    private static PreviewProtocol.Transfer transfer;
    private static long armedAt;
    private static int openings;
    private static final net.minecraft.resources.ResourceLocation IMAGE=Elsebase.id("dynamic/arrival");
    private static boolean image;
    private static long imageExpires;
    private static long fadeUntil;
    public static int capturedArrivals;
    public static int replacedScreens;
    private PortalTransition() {}
    public static void arm(PreviewProtocol.Transfer value) {
        var mc=Minecraft.getInstance();
        if(!PreviewSettings.TRANSITION.get() || mc.level==null || mc.player==null || !mc.player.isAlive()
                || !mc.level.dimension().equals(value.source().dimension()) || mc.player.distanceToSqr(value.source().center())>4*4) return;
        clear(); transfer=value; armedAt=Util.getMillis(); openings=0;
        if(PreviewClient.scene!=null && !ShaderCompatibility.activeShaders()) {
            try {
                var texture=PreviewClient.renderer.captureArrival(PreviewClient.scene,value);
                if(texture!=null) { mc.getTextureManager().register(IMAGE,texture); image=true; imageExpires=armedAt+31_000; capturedArrivals++; }
            } catch(RuntimeException | LinkageError failure) {
                dev.elsebase.preview.ModelQuarantine.rethrowFatal(failure);
                Elsebase.LOGGER.warn("Portal arrival image unavailable; using transition veil",failure);
            }
        }
    }
    public static void clear() {
        transfer=null; openings=0; fadeUntil=0;
        if(image) { Minecraft.getInstance().getTextureManager().release(IMAGE); image=false; }
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if(transfer!=null && Util.getMillis()-armedAt>2000 || image && (Util.getMillis()>imageExpires || Minecraft.getInstance().screen==null && transfer==null && Util.getMillis()>=fadeUntil)) clear();
    }
    @SubscribeEvent public static void fade(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(!image || mc.screen!=null || fadeUntil<=Util.getMillis()) return;
        var graphics=event.getGuiGraphics();
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        var color=com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        try {
            graphics.setColor(1,1,1,Math.min(1,(fadeUntil-Util.getMillis())/150f));
            graphics.blit(IMAGE,0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0,0,1,1,1,1);
        } finally {
            graphics.setColor(color[0],color[1],color[2],color[3]);
            if(!blend) com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
    }
    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        if(transfer==null || event.getNewScreen() instanceof Bridge) return;
        var mc=Minecraft.getInstance(); long age=Util.getMillis()-armedAt;
        if(age>2000 || mc.level==null || mc.player==null || !mc.player.isAlive()) { clear(); return; }
        if(!(event.getNewScreen() instanceof ReceivingLevelScreen receiving)) {
            if(event.getNewScreen()!=null) clear();
            return;
        }
        var dimension=mc.level.dimension();
        boolean expected=openings==0?dimension.equals(transfer.source().dimension()):dimension.equals(transfer.target().dimension());
        if(!expected || openings>=2) { clear(); return; }
        event.setNewScreen(new Bridge(receiving,armedAt)); openings++; replacedScreens++;
        if(openings==2) transfer=null;
    }
    /** Delegates tick/readiness and timeout to the original receiving screen, including other mods' subclasses. */
    private static final class Bridge extends Screen {
        private final ReceivingLevelScreen original;
        private final long started;
        Bridge(ReceivingLevelScreen original,long started) { super(Component.empty()); this.original=original;this.started=started; }
        @Override protected void init() { original.init(minecraft,width,height); }
        @Override public void tick() {
            original.tick();
            if(image && minecraft.screen==null) fadeUntil=Util.getMillis()+150;
        }
        @Override public void render(GuiGraphics graphics,int x,int y,float partialTick) {
            // A restrained portal veil is independent of preview/shader availability and never reveals an unready world.
            if(image) graphics.blit(IMAGE,0,0,width,height,0,0,1,1,1,1);
            else graphics.fillGradient(0,0,width,height,0xff121820,0xff312139);
            if(Util.getMillis()-started>750) graphics.drawCenteredString(font,Component.translatable("elsebase.portal.loading"),width/2,height/2,0xffd5c9df);
        }
        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public void removed() { original.removed(); }
    }
}
