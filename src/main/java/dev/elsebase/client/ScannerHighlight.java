package dev.elsebase.client;

import dev.elsebase.*;
import dev.elsebase.template.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Bounded, depth-tested scanner feedback: invalid cells are red before the authoritative scan is submitted. */
@EventBusSubscriber(modid=Elsebase.ID,value=Dist.CLIENT)
public final class ScannerHighlight {
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc=Minecraft.getInstance(); if(mc.player==null || mc.level==null || mc.screen!=null || !(mc.player.getMainHandItem().is(Content.SCANNER.get()) || mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().is(Content.SCANNER.get()))) return;
        var panel=TemplateScanner.target(mc.level,mc.player.position(),mc.player.getEyePosition(),mc.player.getLookAngle()); if(panel==null) return;
        try {
            var points=panel.positions(true); var buffers=mc.renderBuffers().bufferSource(); var camera=event.getCamera().getPosition();
            for(var point:points) {
                var state=mc.level.getBlockState(point); boolean valid=state.isAir() || StructuralBlock.protectedStructure(mc.level,state) || Materials.problem(state)==null && TemplateModels.supported(Materials.describe(state));
                var box=new AABB(point).inflate(.004).move(-camera.x,-camera.y,-camera.z);
                LevelRenderer.addChainedFilledBoxVertices(event.getPoseStack(),buffers.getBuffer(RenderType.debugFilledBox()),box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,valid?.1f:1,valid?.8f:.05f,.1f,valid?.08f:.35f);
                LevelRenderer.renderLineBox(event.getPoseStack(),buffers.getBuffer(RenderType.lines()),box,valid?.1f:1,valid?.8f:.05f,.1f,.8f);
                if(!valid && mc.hitResult instanceof BlockHitResult hit && point.equals(hit.getBlockPos())) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(Materials.problem(state)==null?"Unsupported material model":Materials.problem(state)),true);
            }
            buffers.endBatch(RenderType.debugFilledBox()); buffers.endBatch(RenderType.lines());
        } catch(Pattern.Rejected invalid) { mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(invalid.getMessage()),true); }
    }
    private ScannerHighlight() {}
}
