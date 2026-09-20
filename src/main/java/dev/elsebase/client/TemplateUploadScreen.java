package dev.elsebase.client;

import dev.elsebase.template.Pattern;
import dev.elsebase.template.Theme;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

/** Names an upload or server scan and requires explicit confirmation before replacing a shared server definition. */
public final class TemplateUploadScreen extends Screen {
    private final Screen parent;
    private final Theme pattern;
    private final String id;
    private final boolean scan;
    private EditBox name;
    private String value,error="";
    public TemplateUploadScreen(Screen parent,Theme pattern,String id,boolean scan) { super(Component.literal(scan?"Save scanned theme":id.isEmpty()?"Import theme":"Overwrite theme")); this.parent=parent; this.pattern=pattern; this.id=id; this.scan=scan; value=pattern.name(); }
    @Override protected void init() {
        name=addRenderableWidget(new EditBox(font,width/2-130,65,260,20,Component.literal("Template name"))); name.setMaxLength(64); name.setValue(value); name.setResponder(text -> value=text);
        addRenderableWidget(Button.builder(Component.literal(id.isEmpty()?"Save":scan && !owned()?"Save copy":"Overwrite…"),b -> {
            if(id.isEmpty() || scan && !owned()) save(); else minecraft.setScreen(new ConfirmScreen(ok -> { minecraft.setScreen(this); if(ok) save(); },Component.literal("Update every surface using this template?"),Component.literal("Choose No to keep the existing template.")));
        }).bounds(width/2-130,98,125,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b -> onClose()).bounds(width/2+5,98,125,20).build());
    }
    private boolean owned() { return minecraft.player!=null && id.startsWith("player/"+minecraft.player.getUUID()+"/"); }
    private void save() {
        try { var candidate=pattern.named(value); TemplateClient.request(scan?"save_scan":"import",id,scan?value.getBytes(java.nio.charset.StandardCharsets.UTF_8):candidate.bytes()); onClose(); }
        catch(Pattern.Rejected failure) { error=failure.getMessage(); }
    }
    @Override public void render(GuiGraphics g,int x,int y,float delta) { g.fill(0,0,width,height,0xee101923); super.render(g,x,y,delta); g.drawCenteredString(font,title,width/2,30,0xffffff); g.drawWordWrap(font,Component.literal(error.isEmpty()?"Built-in and other players' themes are saved as your own copy. Your own theme updates everywhere it is used.":error),width/2-130,132,260,0xc8ced6); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
