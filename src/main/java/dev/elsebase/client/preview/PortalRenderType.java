package dev.elsebase.client.preview;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Already-lit destination image writes source-plane depth, preventing later particles/entities from bleeding through. */
public final class PortalRenderType extends RenderType {
    public static final RenderType VIEW=create("elsebase_portal_view",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(PreviewRenderer.TEXTURE,false,false))
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_DEPTH_WRITE).setOverlayState(OVERLAY).createCompositeState(false));
    private PortalRenderType() { super("elsebase_surface",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,() -> {},() -> {}); }
}
