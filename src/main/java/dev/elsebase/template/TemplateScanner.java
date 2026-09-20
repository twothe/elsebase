package dev.elsebase.template;

import dev.elsebase.*;
import dev.elsebase.structure.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;

/** Private full-panel scan buffers; scans never publish a theme or modify source blocks. */
public final class TemplateScanner {
    public record Draft(String base,Theme theme,Set<String> changed) {
        public Draft { changed=Set.copyOf(changed); }
        public boolean unsaved() { return base.isEmpty() || !changed.isEmpty(); }
    }
    private static Map<UUID,Draft> drafts(ServerPlayer player) { return TemplateState.get(player.server).scans; }
    public static Draft draft(ServerPlayer player) {
        var draft=drafts(player).get(player.getUUID()); if(draft==null) throw new Pattern.Rejected("Shift-right-click and select a theme to edit first"); return draft;
    }
    public static String selected(ServerPlayer player) { var draft=drafts(player).get(player.getUUID()); return draft==null?"":draft.base(); }
    public static void select(ServerPlayer player,String id,boolean discard) {
        var previous=drafts(player).get(player.getUUID());
        if(previous!=null && previous.base().equals(id)) { TemplateState.get(player.server).setDirty(); sync(player); return; }
        if(previous!=null && previous.unsaved() && !discard) throw new Pattern.Rejected("Unsaved scans: save them or confirm switching themes");
        var entry=TemplateState.get(player.server).entries.get(id); if(entry==null) throw new Pattern.Rejected("Theme no longer exists");
        drafts(player).put(player.getUUID(),new Draft(id,entry.theme(),Set.of())); TemplateState.get(player.server).setDirty(); sync(player);
    }
    public static void saved(ServerPlayer player,String id,Theme theme) { drafts(player).put(player.getUUID(),new Draft(id,theme,Set.of())); TemplateState.get(player.server).setDirty(); sync(player); }
    /** A new private neutral draft is not registered, charged against quota or published until saved. */
    public static void create(ServerPlayer player,String name,boolean discard) {
        var previous=drafts(player).get(player.getUUID());
        if(previous!=null && previous.unsaved() && !discard) throw new Pattern.Rejected("Save your draft or confirm discarding it first");
        var pattern=new Pattern(name,player.getGameProfile().getName(),1,1,List.of(new Pattern.Material("minecraft:stone",Map.of())),List.of(0));
        var theme=new Theme(name,pattern.author(),pattern,pattern,pattern);
        drafts(player).put(player.getUUID(),new Draft("",theme,Set.of())); TemplateState.get(player.server).setDirty(); sync(player);
    }
    public static void sync(ServerPlayer player) {
        var draft=drafts(player).get(player.getUUID());
        if(draft==null) { TemplateServer.send(player,"scan_clear","",new byte[0]); return; }
        var current=TemplateState.get(player.server).entries.get(draft.base());
        if(draft.changed().isEmpty() && current!=null && !current.theme().equals(draft.theme())) {
            draft=new Draft(draft.base(),current.theme(),Set.of()); drafts(player).put(player.getUUID(),draft); TemplateState.get(player.server).setDirty();
        }
        TemplateServer.send(player,"scan_draft",draft.base(),draft.theme().bytes());
        TemplateServer.send(player,"scan_changes","",String.join(", ",new TreeSet<>(draft.changed())).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static StructuralEditor.Panel target(net.minecraft.world.level.Level level,net.minecraft.world.phys.Vec3 feet,net.minecraft.world.phys.Vec3 eye,net.minecraft.world.phys.Vec3 look) {
        return level.dimension().equals(Elsebase.DIMENSION)?PanelSelection.scan(level,feet,eye,look):null;
    }
    /** Empty apertures keep buffered materials. Invalid nonempty cells reject the complete operation. */
    public static void scan(ServerPlayer player) {
        try {
            var draft=draft(player);
            if(player.isSpectator()) throw new Pattern.Rejected("Spectators cannot scan themes");
            var panel=target(player.level(),player.position(),player.getEyePosition(),player.getLookAngle());
            if(panel==null) throw new Pattern.Rejected("Look at a room wall, floor or ceiling in the Backdoor");
            var pattern=capture(player,panel,draft.theme().face(panel.side()));
            var changed=new HashSet<>(draft.changed()); changed.add(Theme.role(panel.side()));
            var updated=new Draft(draft.base(),draft.theme().withFace(panel.side(),pattern),changed); updated.theme().bytes();
            drafts(player).put(player.getUUID(),updated); TemplateState.get(player.server).setDirty(); sync(player);
            TemplateServer.message(player,"elsebase.message.scan_complete");
        } catch(Pattern.Rejected error) { TemplateServer.message(player,error.getMessage()); }
        catch(RuntimeException | LinkageError error) { dev.elsebase.preview.ModelQuarantine.rethrowFatal(error); Elsebase.LOGGER.error("Room scan failed without modifying sources for {}",player.getUUID(),error); TemplateServer.message(player,"elsebase.message.scan_failed"); }
    }
    /** Uses the renderer's UV address to preserve asymmetric, multi-block designs on every wall orientation. */
    public static Pattern capture(ServerPlayer player,StructuralEditor.Panel panel,Pattern previous) {
        int width=panel.side().getAxis()==Direction.Axis.Y?16:14;
        int height=panel.side().getAxis()==Direction.Axis.Y?16:(int)panel.bounds(true).getYsize();
        var grid=new Pattern.Material[width*height]; int nonempty=0;
        for(var pos:panel.positions(true)) {
            if(!player.serverLevel().hasChunkAt(pos)) throw new Pattern.Rejected("Scan includes unloaded blocks");
            Direction face=panel.side().getOpposite(); var address=SurfaceAddress.at(pos,face);
            int u=address.u(),v=address.v(); var state=player.level().getBlockState(pos); Pattern.Material material;
            if(state.isAir()) material=previous.at(u,v);
            else {
                nonempty++;
                if(StructuralBlock.protectedStructure(player.level(),state)) material=TemplateServer.appearance(player.server,pos,face);
                else { String problem=Materials.problem(state); if(problem!=null) throw new Pattern.Rejected("Unsuitable block at "+pos.toShortString()+": "+problem); material=Materials.describe(MaterialOrientation.toPattern(state,panel.side())); }
            }
            grid[v*width+u]=material;
        }
        if(nonempty==0) throw new Pattern.Rejected("This surface is empty; nothing to scan");
        var palette=new ArrayList<Pattern.Material>(); var cells=new ArrayList<Integer>();
        for(var material:grid) { if(material==null) throw new IllegalStateException("Incomplete scan grid"); int index=palette.indexOf(material); if(index<0) { index=palette.size(); palette.add(material); } cells.add(index); }
        return new Pattern(previous.name(),player.getGameProfile().getName(),width,height,palette,cells);
    }
    private TemplateScanner() {}
}
