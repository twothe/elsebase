package dev.elsebase.client;

import dev.elsebase.Elsebase;
import dev.elsebase.ToolItem;
import dev.elsebase.structure.PanelSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Translucent, depth-tested preview of the exact room surface submitted by structural tools. */
@EventBusSubscriber(modid = Elsebase.ID, value = Dist.CLIENT)
public final class StructuralPreview {
    private StructuralPreview() {}
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player==null || minecraft.level==null || minecraft.screen!=null || player.isSpectator()
                || !minecraft.level.dimension().equals(Elsebase.DIMENSION)) return;
        var main = player.getMainHandItem().getItem();
        var off = player.getOffhandItem().getItem();
        ToolItem tool = main instanceof ToolItem item && item.structural() ? item
                : player.getMainHandItem().isEmpty() && off instanceof ToolItem item && item.structural() ? item : null;
        if (tool==null) return;
        var panel = PanelSelection.select(minecraft.level,player.position(),player.getEyePosition(),player.getLookAngle(),tool.restores());
        if (panel==null) return;
        var camera = event.getCamera().getPosition();
        var box = panel.bounds(tool.restores()).inflate(0.006).move(-camera.x,-camera.y,-camera.z);
        float red = tool.restores() ? 0.15f : 1.0f;
        float green = tool.restores() ? 0.9f : 0.35f;
        var pose = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        LevelRenderer.addChainedFilledBoxVertices(pose,buffers.getBuffer(RenderType.debugFilledBox()),
                box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,red,green,0.35f,0.22f);
        buffers.endBatch(RenderType.debugFilledBox());
        LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),box,red,green,0.35f,0.85f);
        buffers.endBatch(RenderType.lines());
    }
}
