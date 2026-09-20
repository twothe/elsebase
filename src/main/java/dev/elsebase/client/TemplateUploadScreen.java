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
    public TemplateUploadScreen(Screen parent,Theme pattern,String id,boolean scan) { super(Component.literal(scan?UiText.text("elsebase.ui.save_scanned_theme"):id.isEmpty()?UiText.text("elsebase.ui.import_theme"):UiText.text("elsebase.ui.overwrite_theme"))); this.parent=parent; this.pattern=pattern; this.id=id; this.scan=scan; value=pattern.name(); }
    @Override protected void init() {
        name=addRenderableWidget(new EditBox(font,width/2-130,65,260,20,Component.literal(UiText.text("elsebase.ui.template_name")))); name.setMaxLength(64); name.setValue(value); name.setResponder(text -> value=text);
        addRenderableWidget(Button.builder(Component.literal(id.isEmpty()?UiText.text("elsebase.ui.save"):scan && !owned()?UiText.text("elsebase.ui.save_copy"):UiText.text("elsebase.ui.overwrite")),b -> {
            if(id.isEmpty() || scan && !owned()) save(); else minecraft.setScreen(new ConfirmScreen(ok -> { minecraft.setScreen(this); if(ok) save(); },Component.literal(UiText.text("elsebase.ui.update_every_surface_using_this_template")),Component.literal(UiText.text("elsebase.ui.choose_no_to_keep_the_existing_template"))));
        }).bounds(width/2-130,98,125,20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.text("elsebase.ui.cancel")),b -> onClose()).bounds(width/2+5,98,125,20).build());
    }
    private boolean owned() { return minecraft.player!=null && id.startsWith("player/"+minecraft.player.getUUID()+"/"); }
    private void save() {
        try { var candidate=pattern.named(value); TemplateClient.request(scan?"save_scan":"import",id,scan?value.getBytes(java.nio.charset.StandardCharsets.UTF_8):candidate.bytes()); onClose(); }
        catch(Pattern.Rejected failure) { error=failure.getMessage(); }
    }
    @Override public void render(GuiGraphics g,int x,int y,float delta) { g.fill(0,0,width,height,0xee101923); super.render(g,x,y,delta); g.drawCenteredString(font,title,width/2,30,0xffffff); g.drawWordWrap(font,Component.literal(error.isEmpty()?UiText.text("elsebase.ui.built_in_and_other_players_themes_are_saved_as_your_own_copy_your_own_theme_updates_everywhere_it_is_used"):error),width/2-130,132,260,0xc8ced6); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
