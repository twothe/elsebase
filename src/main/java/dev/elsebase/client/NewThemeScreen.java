package dev.elsebase.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Names a neutral scanner draft; server-side validation protects the existing buffer until creation succeeds. */
public final class NewThemeScreen extends Screen {
    private final Screen parent;
    private final boolean discard;
    private String name=UiText.text("elsebase.ui.new_theme");
    public NewThemeScreen(Screen parent,boolean discard) { super(Component.literal(UiText.text("elsebase.ui.new_theme"))); this.parent=parent; this.discard=discard; }
    @Override protected void init() {
        var field=addRenderableWidget(new EditBox(font,width/2-130,65,260,20,Component.literal(UiText.text("elsebase.ui.theme_name")))); field.setMaxLength(64); field.setValue(name); field.setResponder(value -> name=value);
        addRenderableWidget(Button.builder(Component.literal(UiText.text("elsebase.ui.create")),button -> {
            if(name.isBlank()) return;
            TemplateClient.request(discard?"discard_new_scan":"new_scan","",name.getBytes(java.nio.charset.StandardCharsets.UTF_8)); onClose();
        }).bounds(width/2-130,98,125,20).build());
        addRenderableWidget(Button.builder(Component.literal(UiText.text("elsebase.ui.cancel")),button -> onClose()).bounds(width/2+5,98,125,20).build());
        setInitialFocus(field);
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float delta) {
        graphics.fill(0,0,width,height,0xee101923); super.render(graphics,x,y,delta); graphics.drawCenteredString(font,title,width/2,30,0xffffff);
        graphics.drawWordWrap(font,Component.literal(UiText.text("elsebase.ui.start_with_plain_stone_scan_walls_floor_and_ceiling_then_preview_and_save_your_theme")),width/2-130,132,260,0xc8ced6);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
