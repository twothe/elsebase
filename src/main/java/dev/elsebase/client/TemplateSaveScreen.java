package dev.elsebase.client;

import dev.elsebase.template.Theme;
import java.nio.file.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

/** Local export dialog with explicit collision confirmation; server display names never become raw filesystem paths. */
public final class TemplateSaveScreen extends Screen {
    private final Screen parent;
    private final Theme pattern;
    private EditBox name;
    private String status="",value;
    public TemplateSaveScreen(Screen parent,Theme pattern) { super(Component.literal("Save to personal library")); this.parent=parent; this.pattern=pattern; value=pattern.name(); }
    @Override protected void init() {
        name=addRenderableWidget(new EditBox(font,width/2-130,70,260,20,Component.literal("Template name"))); name.setMaxLength(64); name.setValue(value); name.setResponder(text -> value=text);
        addRenderableWidget(Button.builder(Component.literal("Save"),button -> save(false)).bounds(width/2-130,100,125,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),button -> onClose()).bounds(width/2+5,100,125,20).build());
    }
    private void save(boolean overwrite) {
        try {
            var copy=pattern.named(value);
            String filename=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(copy.name().toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,24)+".json";
            var path=TemplateClient.library().resolve(filename);
            if(Files.exists(path) && !overwrite) { minecraft.setScreen(new ConfirmScreen(ok -> { minecraft.setScreen(this); if(ok) save(true); },Component.literal("Overwrite existing local template?"),Component.literal("Choose No to rename it instead."))); return; }
            TemplateClient.localSave(copy,path); TemplateClient.message="Saved to elsebase/templates/"+filename; onClose();
        } catch(Exception e) { status=e.getMessage(); }
    }
    @Override public void render(GuiGraphics g,int x,int y,float delta) { g.fill(0,0,width,height,0xee101923); super.render(g,x,y,delta); g.drawCenteredString(font,title,width/2,35,0xffffff); g.drawWordWrap(font,Component.literal(status),width/2-130,135,260,0xff9999); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
