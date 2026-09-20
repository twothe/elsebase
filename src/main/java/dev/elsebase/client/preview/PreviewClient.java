package dev.elsebase.client.preview;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.elsebase.Elsebase;
import dev.elsebase.portal.Endpoint;
import dev.elsebase.preview.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client scene lifecycle and render-stage scheduling. Recoverable renderer failures disable this session's live view. */
@EventBusSubscriber(modid=Elsebase.ID,value=Dist.CLIENT)
public final class PreviewClient {
    public static PreviewScene scene;
    public static PreviewRenderer renderer=new PreviewRenderer();
    private static int tick,lastSceneTick,frame,lastPreference=-1;
    private static boolean failed;
    private static boolean restart;
    private PreviewClient() {}
    public static void install() {
        PreviewProtocol.sceneReceiver=PreviewClient::receive;
        PreviewProtocol.sectionReceiver=value -> { if(scene!=null) scene.accept(value); };
        PreviewProtocol.transferReceiver=PortalTransition::arm;
    }
    private static void receive(PreviewProtocol.Scene value) {
        var mc=Minecraft.getInstance();
        if(mc.level==null || !mc.level.dimension().equals(value.source().dimension()) || mc.player==null
                || mc.player.distanceToSqr(value.source().center())>28*28 || !allowed()) return;
        if(scene==null || scene.description.generation()!=value.generation()) {
            disposeScene(); scene=new PreviewScene(value,mc.level.registryAccess());
        } else if(scene.description.bright()!=value.bright() || Math.abs(scene.description.sky()-value.sky())>.05f) {
            scene.dirty.addAll(scene.sections.keySet());
        }
        scene.description=value; lastSceneTick=tick;
    }
    private static boolean allowed() { return !failed && PreviewSettings.QUALITY.get()!=PreviewSettings.Quality.OFF && !ShaderCompatibility.activeShaders(); }
    public static boolean live(Endpoint source) {
        return allowed() && scene!=null && renderer.ready && scene.description.source().equals(source) && tick-lastSceneTick<=30;
    }
    private static void disposeScene() { renderer.close(); renderer=new PreviewRenderer(); scene=null; }
    public static void resetScene() { disposeScene(); restart=true; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        tick++; var mc=Minecraft.getInstance();
        if(mc.player==null || mc.getConnection()==null) return;
        int preference=allowed()?PreviewSettings.QUALITY.get().ordinal():0;
        if(tick%40==0 || preference!=lastPreference || restart) {
            PacketDistributor.sendToServer(new PreviewProtocol.Preference(preference,restart)); lastPreference=preference; restart=false;
        }
        if(scene!=null && (preference==0 || tick-lastSceneTick>30 || !scene.description.source().dimension().equals(mc.level.dimension()))) resetScene();
        if(scene!=null) renderer.rebuild(scene);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY || scene==null || !allowed()) return;
        var camera=event.getCamera().getPosition();
        if(camera.distanceToSqr(scene.description.source().center())>24*24) return;
        // Never add a second render pass for an off-screen doorway.
        if(!event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(scene.description.source().position()).expandTowards(0,1,0))) return;
        if(++frame%2!=0 && camera.distanceToSqr(scene.description.source().center())>8*8 && renderer.ready) return;
        try { renderer.render(scene,camera); }
        catch(RuntimeException | LinkageError error) {
            ModelQuarantine.rethrowFatal(error); failed=true; resetScene();
            Elsebase.LOGGER.error("Live portal renderer disabled for this resource session; static portal travel remains available",error);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetScene(); lastPreference=-1; PortalTransition.clear();
    }
    @SubscribeEvent public static void shaders(RegisterShadersEvent event) {
        resetScene(); SceneMesh.resetFailures(); failed=false; PreviewRenderer.shader=null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),Elsebase.id("portal_scene"),DefaultVertexFormat.POSITION_TEX_COLOR),shader -> PreviewRenderer.shader=shader);
        } catch(java.io.IOException | RuntimeException | LinkageError failure) {
            ModelQuarantine.rethrowFatal(failure); failed=true;
            Elsebase.LOGGER.warn("Portal scene shader unavailable after resource reload; retaining normal portal surface",failure);
        }
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(dev.elsebase.Content.PORTAL_SURFACE.get(),PortalSurfaceRenderer::new); }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { event.register(PortalSurfaceRenderer.SURFACE); }
}
