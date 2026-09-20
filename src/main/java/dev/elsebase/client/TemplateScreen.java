package dev.elsebase.client;

import dev.elsebase.ToolItem;
import dev.elsebase.template.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

/** One complete-theme browser with small, tool-specific action sets. Scan manages import/export; Paint can delete owned server themes. */
public final class TemplateScreen extends Screen {
    private record Row(String id,String owner,String name,Theme local) {}
    private final List<Row> rows=new ArrayList<>();
    private final ToolItem.Kind mode;
    private String selected,searchText="";
    private int filter,page,offset,searchDelay;
    private Row current;
    private Theme importSource;
    private boolean ceiling,previewBroken;
    private String previewError="";
    public TemplateScreen() {
        super(Component.literal("Elsebase - "+switch(ToolItem.Kind.valueOf(TemplateClient.catalog.get("mode").getAsString())) { case CREATE -> UiText.text("elsebase.ui.construction_theme"); case PAINT -> UiText.text("elsebase.ui.paint_theme"); default -> UiText.text("elsebase.ui.scan_and_edit_themes"); }));
        mode=ToolItem.Kind.valueOf(TemplateClient.catalog.get("mode").getAsString()); selected=TemplateClient.catalog.get("selected").getAsString();
    }
    private boolean scanner() { return mode==ToolItem.Kind.SCAN; }
    public void bufferReplaced(String previous,String next) { if(scanner() && selected.equals(previous)) { selected=next; current=null; } }
    private Button button(String text,int x,int y,int w,Runnable action) { return addRenderableWidget(Button.builder(Component.literal(text),b -> action.run()).bounds(x,y,w,20).build()); }
    private int listBottom() { return height-60; }
    private int visible() { return Math.max(1,(listBottom()-80)/22); }
    @Override protected void init() {
        int left=10,cw=(width-30)/2,right=20+cw;
        var search=addRenderableWidget(new EditBox(font,left,30,cw,20,Component.literal(UiText.text("elsebase.ui.search_themes")))); search.setMaxLength(64); search.setHint(Component.literal(UiText.text("elsebase.ui.search_themes"))); search.setValue(searchText); search.setResponder(value -> { searchText=value; searchDelay=8; });
        button(new String[]{UiText.text("elsebase.ui.all_themes"),UiText.text("elsebase.ui.my_themes"),UiText.text("elsebase.ui.built_in_themes"),UiText.text("elsebase.ui.personal_library")}[filter],left,54,cw,() -> { filter=(filter+1)%(scanner()?4:3); page=0; offset=0; load(); });
        button("<",left,height-58,30,() -> { if(offset>0) { offset=Math.max(0,offset-visible()); rebuildWidgets(); } else if(page>0 && filter!=3) { page--; load(); } });
        button(">",left+cw-30,height-58,30,() -> { if(offset+visible()<rows.size()) { offset+=visible(); rebuildWidgets(); } else if(filter!=3 && page+1<TemplateClient.catalog.get("pages").getAsInt()) { page++; load(); } });
        if(scanner()) button(UiText.text("elsebase.ui.new_theme_button"),right,54,cw/2-3,this::newTheme);
        button(ceiling?UiText.text("elsebase.ui.hide_ceiling"):UiText.text("elsebase.ui.show_ceiling"),scanner()?right+cw/2:right,54,scanner()?cw/2:cw,() -> { ceiling=!ceiling; rebuildWidgets(); });
        if(scanner()) {
            button(UiText.text("elsebase.ui.import"),right,height-82,cw/2-3,() -> {
                if(importSource==null) { filter=3; load(); TemplateClient.message=UiText.text("elsebase.ui.choose_a_local_theme_then_import"); }
                else { String id=current!=null && current.local==null && minecraft.player!=null && current.owner.equals(minecraft.player.getUUID().toString())?current.id:""; minecraft.setScreen(new TemplateUploadScreen(this,importSource,id,false)); }
            }).active=TemplateClient.catalog.get("imports").getAsBoolean();
            button(UiText.text("elsebase.ui.export"),right+cw/2,height-82,cw/2,() -> {
                if(current!=null && current.local!=null) minecraft.setScreen(new TemplateSaveScreen(this,current.local));
                else if(selected.equals(TemplateClient.draftBase) && TemplateClient.draft!=null) TemplateClient.request("export_draft","");
                else if(!selected.isEmpty()) TemplateClient.request("export",selected);
            });
            boolean dirty=hasUnsavedDraft() && selected.equals(TemplateClient.draftBase);
            button(ownDraft()?UiText.text("elsebase.ui.save_changes"):UiText.text("elsebase.ui.save_as_my_theme"),right,height-58,cw,() -> minecraft.setScreen(new TemplateUploadScreen(this,TemplateClient.draft,TemplateClient.draftBase,true))).active=dirty;
        }
        if(mode==ToolItem.Kind.PAINT) {
            button(UiText.text("elsebase.ui.use_as_default"),right,height-60,cw/2-3,() -> minecraft.setScreen(new ConfirmScreen(ok -> { minecraft.setScreen(this); if(ok) TemplateClient.request("default",selected); },Component.literal(UiText.text("elsebase.ui.use_as_region_default")),Component.literal(UiText.text("elsebase.ui.inherited_surfaces_in_your_region_will_change"))))).active=!selected.isEmpty() && TemplateClient.catalog.get("defaults").getAsBoolean();
            button(UiText.text("elsebase.ui.delete"),right+cw/2,height-60,cw/2,() -> minecraft.setScreen(new ConfirmScreen(ok -> { minecraft.setScreen(this); if(ok) TemplateClient.request("delete",selected); },Component.literal(UiText.text("elsebase.ui.delete_this_theme")),Component.literal(UiText.text("elsebase.ui.used_surfaces_will_inherit_their_region_default_this_cannot_be_undone"))))).active=minecraft.player!=null && rows.stream().anyMatch(row -> row.id.equals(selected) && row.owner.equals(minecraft.player.getUUID().toString()));
        }
        int count=2,bw=(width-20)/count;
        button(UiText.text("elsebase.ui.select"),10,height-34,bw-3,this::select).active=!selected.isEmpty() && (current==null || current.local==null);
        button(UiText.text("elsebase.ui.cancel"),10+bw*(count-1),height-34,bw,this::onClose);
        for(int i=0;i<visible() && offset+i<rows.size();i++) { var row=rows.get(offset+i); button((row.id.equals(selected)?"> ":"")+font.plainSubstrByWidth(row.name,cw-22),left,80+i*22,cw,() -> choose(row)); }
    }
    private boolean ownDraft() { return minecraft.player!=null && TemplateClient.draftBase.startsWith("player/"+minecraft.player.getUUID()+"/"); }
    private boolean hasUnsavedDraft() { return TemplateClient.draft!=null && (TemplateClient.draftBase.isEmpty() || !TemplateClient.changes.isEmpty()); }
    private void newTheme() {
        if(hasUnsavedDraft()) minecraft.setScreen(new ConfirmScreen(ok -> { minecraft.setScreen(this); if(ok) minecraft.setScreen(new NewThemeScreen(this,true)); },Component.literal(UiText.text("elsebase.ui.discard_the_current_draft")),Component.literal(UiText.text("elsebase.ui.choose_no_to_keep_it_and_save_it_first"))));
        else minecraft.setScreen(new NewThemeScreen(this,false));
    }
    private void select() {
        if(scanner() && hasUnsavedDraft() && !selected.equals(TemplateClient.draftBase)) {
            minecraft.setScreen(new ConfirmScreen(ok -> { minecraft.setScreen(this); if(ok) TemplateClient.request("discard_select",selected); },Component.literal(UiText.text("elsebase.ui.discard_unsaved_scans")),Component.literal(UiText.text("elsebase.ui.select_no_to_return_and_save_your_current_theme_first"))));
        } else TemplateClient.request("select",selected);
    }
    public void refresh() {
        if(filter==3) return;
        rows.clear(); if(TemplateClient.catalog.has("rows")) for(var value:TemplateClient.catalog.getAsJsonArray("rows")) { var row=value.getAsJsonObject(); rows.add(new Row(row.get("id").getAsString(),row.get("owner").getAsString(),UiText.theme(row.get("id").getAsString(),row.get("name").getAsString()),null)); }
        offset=0; if(font!=null) rebuildWidgets();
    }
    @Override public void added() { refresh(); }
    private void choose(Row row) {
        current=row; selected=row.local==null?row.id:""; previewError=""; previewBroken=false;
        if(row.local!=null) importSource=row.local; else TemplateClient.request("get",row.id);
        rebuildWidgets();
    }
    private void load() {
        offset=0;
        if(filter==3) {
            rows.clear(); rebuildWidgets(); String query=searchText; var directory=TemplateClient.library(); var client=minecraft;
            TemplateClient.message=UiText.text("elsebase.ui.reading_personal_library");
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                var found=new ArrayList<Row>();
                try { Files.createDirectories(directory); try(var paths=Files.list(directory)) {
                    for(var path:paths.filter(p -> p.toString().endsWith(".json")).sorted().limit(4096).toList()) try {
                        if(Files.isSymbolicLink(path) || !Files.isRegularFile(path)) continue;
                        var theme=Theme.readFile(path);
                        if(theme.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) found.add(new Row(path.getFileName().toString(),"",theme.name(),theme));
                    } catch(java.io.IOException | Pattern.Rejected invalid) { /* Unsupported local entries cannot be uploaded. */ }
                } } catch(java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                return List.copyOf(found);
            }).whenComplete((found,failure) -> client.execute(() -> {
                if(client.screen!=this || filter!=3 || !searchText.equals(query)) return;
                rows.clear(); if(found!=null) rows.addAll(found); TemplateClient.message=failure==null?UiText.text("elsebase.ui.local_count",rows.size()):UiText.text("elsebase.ui.cannot_read_personal_library_check_folder_permissions"); rebuildWidgets();
            }));
        } else TemplateClient.request("catalog",Integer.toString(page),(new String[]{"all","own","builtin"}[filter]+"\n"+UiText.themeSearch(searchText)).getBytes(StandardCharsets.UTF_8));
    }
    @Override public void tick() { if(searchDelay>0 && --searchDelay==0) { page=0; load(); } }
    private Theme preview() {
        if(current!=null && current.local!=null) return current.local;
        if(scanner() && selected.equals(TemplateClient.draftBase)) return TemplateClient.draft;
        return TemplateClient.THEMES.get(selected);
    }
    @Override public void render(GuiGraphics g,int x,int y,float delta) {
        g.fill(0,0,width,height,0xf0101923); super.render(g,x,y,delta); g.drawCenteredString(font,title,width/2,10,0xf3e9d7);
        int cw=(width-30)/2,right=20+cw; var theme=preview();
        if(theme!=null) g.drawString(font,font.plainSubstrByWidth(UiText.theme(selected,theme.name()),cw),right,33,0xf3e9d7);
        int bottom=height-(scanner()?88:mode==ToolItem.Kind.PAINT?68:48);
        renderRoom(g,right+cw/2,(80+bottom)/2,Math.min(cw/25f,Math.max(1,(bottom-80)/20f)),theme);
        String status=scanner() && hasUnsavedDraft()?(TemplateClient.changes.isEmpty()?UiText.text("elsebase.ui.new_theme_not_published_yet"):UiText.text("elsebase.ui.unsaved",TemplateClient.changes)):TemplateClient.message;
        g.drawString(font,font.plainSubstrByWidth(previewError.isEmpty()?status:previewError,width-20),10,height-11,0xc8ced6);
    }
    /** Preview shows all three multi-block patterns as one room; it never changes a role selection. */
    private void renderRoom(GuiGraphics g,int x,int y,float scale,Theme theme) {
        if(previewBroken || theme==null) return;
        g.flush(); var pose=g.pose(); pose.pushPose();
        try {
            // Camera looks from +X/+Y/+Z into the open corner; the two walls at X=0/Z=0 stay behind the floor.
            pose.translate(x,y,100); pose.scale(scale,-scale,scale); pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(35.264f)); pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-45)); pose.translate(-7,-3.5,-7);
            for(int a=0;a<14;a++) for(int b=0;b<14;b++) { cube(g,theme.floor(),a,0,b,a+1,b+1,net.minecraft.core.Direction.DOWN); if(ceiling) cube(g,theme.ceiling(),a,8,b,a+1,b+1,net.minecraft.core.Direction.UP); }
            for(int a=0;a<14;a++) for(int b=1;b<8;b++) { cube(g,theme.wall(),a,b,0,13-a,b-1,net.minecraft.core.Direction.NORTH); cube(g,theme.wall(),0,b,a,a,b-1,net.minecraft.core.Direction.WEST); }
            g.bufferSource().endBatch();
        } catch(RuntimeException | LinkageError error) { dev.elsebase.preview.ModelQuarantine.rethrowFatal(error); previewBroken=true; dev.elsebase.Elsebase.LOGGER.warn("Theme demo disabled after render failure",error); previewError=UiText.text("elsebase.ui.preview_unavailable_unsupported_material_model"); }
        finally { pose.popPose(); }
    }
    private void cube(GuiGraphics g,Pattern pattern,int x,int y,int z,int u,int v,net.minecraft.core.Direction side) {
        var material=pattern.at(u,v); if(!TemplateModels.supported(material)) { previewError=UiText.text("elsebase.ui.unsupported_material",material.block()); return; }
        var pose=g.pose(); pose.pushPose(); try { pose.translate(x,y,z); minecraft.getBlockRenderer().renderSingleBlock(MaterialOrientation.toWorld(TemplateModels.material(material),side),pose,g.bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY); } finally { pose.popPose(); }
    }
    @Override public boolean isPauseScreen() { return false; }
}
