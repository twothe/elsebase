package dev.elsebase.client;

import dev.elsebase.*;
import dev.elsebase.template.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client library/UI transport and budgeted chunk rebuilds; server definitions remain authoritative. */
@EventBusSubscriber(modid=Elsebase.ID,value=Dist.CLIENT)
public final class TemplateClient {
    public static com.google.gson.JsonObject catalog=new com.google.gson.JsonObject();
    public static Theme draft;
    public static String draftBase="",changes="";
    public static final Map<String,Theme> THEMES=new HashMap<>();
    public static String message="";
    private static final Set<SectionPos> DIRTY=new LinkedHashSet<>();
    private static SectionPos priorityOrigin=SectionPos.of(0,0,0);
    private static final PriorityQueue<SectionPos> NEAREST=new PriorityQueue<>(Comparator.comparingDouble(p -> p.distSqr(priorityOrigin)));
    public static void install() { TemplateProtocol.receiver=TemplateClient::receive; }
    public static void request(String action,String key,byte[] data) { PacketDistributor.sendToServer(new TemplateProtocol.Request(action,key,data)); }
    public static void request(String action,String key) { request(action,key,new byte[0]); }
    public static Path library() { return Minecraft.getInstance().gameDirectory.toPath().resolve("elsebase/templates"); }
    public static void localSave(Theme pattern,Path path) throws java.io.IOException {
        var root=library().toAbsolutePath().normalize(); var target=path.toAbsolutePath().normalize();
        if(!root.equals(target.getParent()) || Files.isSymbolicLink(target)) throw new java.io.IOException("Invalid library path");
        ImportGuard.writeAtomic(target,pattern.bytes());
    }
    public static void receive(TemplateProtocol.Reply packet) {
        var mc=Minecraft.getInstance();
        switch(packet.operation()) {
            case "definition" -> {
                TemplateModels.PATTERNS.put(packet.key(),Pattern.parse(packet.data()));
                TemplateModels.COLUMNS.forEach((key,faces) -> { if(Arrays.asList(faces).contains(packet.key())) dirty(new ChunkPos(key)); });
                refreshPreview();
            }
            case "column" -> {
                long key=Long.parseLong(packet.key()); var payload=Pattern.object(packet.data()); var palette=payload.getAsJsonArray("palette"); var indices=payload.getAsJsonArray("faces");
                if(indices.size()!=96 || palette.isEmpty() || palette.size()>96) throw new IllegalArgumentException("Invalid template column");
                String[] values=new String[96]; for(int i=0;i<96;i++) { int index=Pattern.integer(indices.get(i)); if(index<0 || index>=palette.size()) throw new IllegalArgumentException("Invalid face palette index"); values[i]=Pattern.string(palette.get(index)); }
                if(!TemplateModels.COLUMNS.containsKey(key) && TemplateModels.COLUMNS.size()>=8192) {
                    // Evict one distant historical column, never reset visible assignments during travel.
                    var origin=mc.player==null?new ChunkPos(key):mc.player.chunkPosition();
                    var distant=TemplateModels.COLUMNS.keySet().stream().max(Comparator.comparingDouble(k -> {
                        var pos=new ChunkPos(k); double dx=(double)pos.x-origin.x,dz=(double)pos.z-origin.z; return dx*dx+dz*dz;
                    }));
                    distant.ifPresent(TemplateModels.COLUMNS::remove);
                }
                TemplateModels.COLUMNS.put(key,values); dirty(new ChunkPos(key)); refreshPreview();
            }
            case "open","catalog" -> { catalog=Pattern.object(packet.data()); if(packet.operation().equals("open")) mc.setScreen(new TemplateScreen()); else if(mc.screen instanceof TemplateScreen screen) screen.refresh(); }
            case "theme" -> { THEMES.put(packet.key(),Theme.parse(packet.data())); }
            case "scan_clear" -> { draft=null; draftBase=""; changes=""; }
            case "scan_draft" -> { String previous=draftBase; draft=Theme.parse(packet.data()); draftBase=packet.key(); if(mc.screen instanceof TemplateScreen screen) screen.bufferReplaced(previous,draftBase); }
            case "scan_changes" -> { changes=new String(packet.data(),StandardCharsets.UTF_8); if(mc.screen instanceof TemplateScreen screen) screen.refresh(); }
            case "selected" -> { if(mc.screen instanceof TemplateScreen) mc.setScreen(null); }
            case "export" -> { var theme=Theme.parse(packet.data()); mc.setScreen(new TemplateSaveScreen(mc.screen,theme)); }
            case "message" -> { message=new String(packet.data(),StandardCharsets.UTF_8); if(mc.player!=null) mc.player.displayClientMessage(Component.literal(message),false); }
            default -> throw new IllegalArgumentException("Unknown template reply");
        }
    }
    private static void refreshPreview() { dev.elsebase.client.preview.PreviewClient.templatesChanged(); }
    /** Appearance packets can precede chunk installation. Requeue cached styles once their real chunk arrives. */
    @SubscribeEvent public static void chunkLoaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if(event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level && level.dimension().equals(Elsebase.DIMENSION)) {
            var pos=event.getChunk().getPos(); Minecraft.getInstance().execute(() -> dirty(pos));
        }
    }
    @SubscribeEvent public static void levelLoaded(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        if(event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level) TemplateModels.inBackdoor=level.dimension().equals(Elsebase.DIMENSION);
    }
    private static void dirty(ChunkPos pos) { for(int y=0;y<8;y++) { var section=SectionPos.of(pos,y); if(DIRTY.add(section)) NEAREST.add(section); } }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance(); TemplateModels.inBackdoor=mc.level!=null && mc.level.dimension().equals(Elsebase.DIMENSION);
        if(!TemplateModels.inBackdoor || mc.player==null) return;
        var origin=SectionPos.of(mc.player.blockPosition()); if(!origin.equals(priorityOrigin)) { priorityOrigin=origin; NEAREST.clear(); NEAREST.addAll(DIRTY); }
        for(int i=0;i<4 && !NEAREST.isEmpty();i++) { var section=NEAREST.remove(); DIRTY.remove(section);
            if(mc.level.hasChunk(section.x(),section.z())) mc.levelRenderer.setSectionDirty(section.x(),section.y(),section.z());
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { TemplateModels.clear(); DIRTY.clear(); NEAREST.clear(); catalog=new com.google.gson.JsonObject(); draft=null; draftBase=""; changes=""; THEMES.clear(); }
    private TemplateClient() {}
}
