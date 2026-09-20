package dev.elsebase.template;

import com.google.gson.*;
import dev.elsebase.*;
import dev.elsebase.portal.*;
import dev.elsebase.structure.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** Authoritative template operations and bounded watched-column delivery. No operation loads a chunk. */
public final class TemplateServer {
    private record Delivery(UUID player,long chunk) {}
    private record Upload(UUID player,TemplateProtocol.Request request) {}
    private static final Map<UUID,Set<Long>> WATCHED=new HashMap<>();
    private static final Map<UUID,LinkedHashSet<Long>> PENDING=new LinkedHashMap<>();
    private static final ArrayDeque<UUID> READY=new ArrayDeque<>();
    private static final Map<Delivery,Long> SENT=new HashMap<>();
    private static final Map<UUID,Map<String,Pattern>> DEFINITIONS=new HashMap<>();
    private static final Map<UUID,Integer> LAST=new HashMap<>();
    private static final ArrayDeque<Upload> UPLOADS=new ArrayDeque<>();
    private static ImportGuard guard;
    private static Path directory;
    private static long observed=-1;
    private static int homes=-1;
    private static int appearanceBytes;
    private TemplateServer() {}
    public static void start(MinecraftServer server) {
        directory=server.getServerDirectory().resolve("elsebase/templates"); guard=new ImportGuard(directory.getParent().resolve("import-blocklist.txt"));
        if(!guard.available()) Elsebase.LOGGER.error("Elsebase uploads disabled: import blocklist cannot be read; repair it and restart");
        reload(server);
    }
    public static void clear() { WATCHED.clear(); PENDING.clear(); READY.clear(); SENT.clear(); DEFINITIONS.clear(); LAST.clear(); UPLOADS.clear(); guard=null; observed=-1; homes=-1; }
    public static void logout(UUID id) { WATCHED.remove(id); PENDING.remove(id); READY.remove(id); SENT.keySet().removeIf(k -> k.player.equals(id)); DEFINITIONS.remove(id); LAST.remove(id); UPLOADS.removeIf(k -> k.player.equals(id)); }
    public static String globalStyle() {
        String value=Settings.TEMPLATE_DEFAULT.get();
        return Arrays.asList(TemplateState.THEMES).contains(value)?TemplateState.builtin(value):value;
    }
    public static ToolItem.Kind heldMode(ServerPlayer player) {
        for(var hand:net.minecraft.world.InteractionHand.values()) if(player.getItemInHand(hand).getItem() instanceof ToolItem tool && tool.themeTool()) return tool.kind();
        throw new Pattern.Rejected("Hold a Construction, Paint or Scan Tool");
    }
    public static String selection(ServerPlayer player,ToolItem.Kind mode) {
        var state=TemplateState.get(player.server);
        String id=state.choices.getOrDefault(player.getUUID(),new EnumMap<>(ToolItem.Kind.class)).getOrDefault(mode,mode==ToolItem.Kind.PAINT?"":TemplateState.builtin("quiet_workshop"));
        return state.entries.containsKey(id)?id:"";
    }
    public static void watch(ServerPlayer player,ChunkPos chunk,boolean watching) {
        if(!player.connection.hasChannel(TemplateProtocol.Reply.TYPE)) return;
        if(watching) { SENT.remove(new Delivery(player.getUUID(),chunk.toLong())); WATCHED.computeIfAbsent(player.getUUID(),id -> new HashSet<>()).add(chunk.toLong()); enqueue(new Delivery(player.getUUID(),chunk.toLong())); }
        else { WATCHED.getOrDefault(player.getUUID(),Collections.emptySet()).remove(chunk.toLong()); SENT.remove(new Delivery(player.getUUID(),chunk.toLong())); }
    }
    public static void tick(MinecraftServer server) {
        appearanceBytes=0;
        var state=TemplateState.get(server); int count=WorldState.get(server).homes.size();
        if(observed!=state.generation || homes!=count) { observed=state.generation; homes=count; SENT.clear(); PENDING.clear(); READY.clear(); WATCHED.forEach((id,chunks) -> { var player=server.getPlayerList().getPlayer(id); if(player!=null) chunks.stream().sorted(Comparator.comparingDouble(key -> { var pos=new ChunkPos(key); return player.distanceToSqr(pos.getMiddleBlockX(),player.getY(),pos.getMiddleBlockZ()); })).forEach(chunk -> enqueue(new Delivery(id,chunk))); }); }
        for(int i=0;i<2 && !UPLOADS.isEmpty();i++) {
            var upload=UPLOADS.remove(); var player=server.getPlayerList().getPlayer(upload.player); if(player!=null) perform(player,upload.request);
        }
        long start=System.nanoTime();
        for(int i=0;i<4 && !READY.isEmpty();i++) {
            var id=READY.remove(); var chunks=PENDING.get(id); var iterator=chunks.iterator(); var next=new Delivery(id,iterator.next()); iterator.remove();
            if(chunks.isEmpty()) PENDING.remove(id); else READY.add(id);
            var player=server.getPlayerList().getPlayer(next.player);
            if(player!=null && WATCHED.getOrDefault(next.player,Set.of()).contains(next.chunk)) syncColumn(player,new ChunkPos(next.chunk));
            if(System.nanoTime()-start>2_000_000) break;
        }
    }
    private static void enqueue(Delivery delivery) {
        var chunks=PENDING.get(delivery.player); if(chunks==null) { chunks=new LinkedHashSet<>(); PENDING.put(delivery.player,chunks); READY.add(delivery.player); }
        chunks.add(delivery.chunk);
    }
    /** A fresh preview has no guaranteed column cache; invalidate only its bounded destination footprint. */
    public static void forgetColumns(UUID player,Collection<ChunkPos> columns) {
        for(var column:columns) SENT.remove(new Delivery(player,column.toLong()));
    }
    /** Also used only after preview ownership/distance validation; this publishes appearance, never world block contents. */
    public static void syncColumn(ServerPlayer player,ChunkPos pos) {
        if(!player.connection.hasChannel(TemplateProtocol.Reply.TYPE)) return;
        var state=TemplateState.get(player.server); var key=new Delivery(player.getUUID(),pos.toLong());
        if(Objects.equals(SENT.get(key),state.generation)) return;
        String[] faces=state.column(player.server,pos.x,pos.z); var definitions=DEFINITIONS.computeIfAbsent(player.getUUID(),id -> new HashMap<>());
        for(String id:new HashSet<>(List.of(faces))) { var pattern=state.pattern(id); if(definitions.get(id)!=pattern) {
            byte[] bytes=pattern.bytes(); if(appearanceBytes+bytes.length>131072) { if(WATCHED.getOrDefault(player.getUUID(),Set.of()).contains(pos.toLong())) enqueue(key); return; }
            appearanceBytes+=bytes.length; send(player,"definition",id,bytes); definitions.put(id,pattern);
        } }
        var palette=new ArrayList<String>(); var indices=new JsonArray();
        for(String id:faces) { int index=palette.indexOf(id); if(index<0) { index=palette.size(); palette.add(id); } indices.add(index); }
        var payload=new JsonObject(); payload.add("palette",Pattern.JSON.toJsonTree(palette)); payload.add("faces",indices); byte[] bytes=Pattern.JSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        if(appearanceBytes+bytes.length>131072) { if(WATCHED.getOrDefault(player.getUUID(),Set.of()).contains(pos.toLong())) enqueue(key); return; }
        appearanceBytes+=bytes.length;
        send(player,"column",Long.toString(pos.toLong()),bytes);
        if(SENT.size()>32768) SENT.clear(); SENT.put(key,state.generation);
    }
    public static void send(ServerPlayer player,String operation,String key,byte[] bytes) {
        if(player.connection.hasChannel(TemplateProtocol.Reply.TYPE)) PacketDistributor.sendToPlayer(player,new TemplateProtocol.Reply(operation,key,bytes));
    }
    public static void message(ServerPlayer player,String text) { send(player,"message","",text.getBytes(StandardCharsets.UTF_8)); }
    public static void open(ServerPlayer player) {
        try { if(heldMode(player)==ToolItem.Kind.SCAN) TemplateScanner.sync(player); catalog(player,0,true,"all",""); }
        catch(Pattern.Rejected failure) { message(player,failure.getMessage()); }
    }
    private static void catalog(ServerPlayer player,int page,boolean open,String filter,String search) {
        var mode=heldMode(player); var state=TemplateState.get(player.server);
        var entries=new ArrayList<>(state.entries.values().stream().filter(e -> e.theme().name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)) && (!filter.equals("own") || e.owner().equals(player.getUUID().toString())) && (!filter.equals("builtin") || e.id().startsWith("builtin/"))).toList());
        int pages=Math.max(1,(entries.size()+63)/64); page=Math.clamp(page,0,pages-1);
        var root=new JsonObject(); root.addProperty("page",page); root.addProperty("pages",pages); root.addProperty("mode",mode.name());
        root.addProperty("imports",Settings.TEMPLATE_IMPORTS.get() && guard!=null && guard.allowed(player.getUUID()));
        root.addProperty("defaults",Settings.PERSONAL_TEMPLATES.get());
        var rows=new JsonArray();
        for(var entry:entries.subList(page*64,Math.min(entries.size(),(page+1)*64))) { var row=new JsonObject(); row.addProperty("id",entry.id()); row.addProperty("owner",entry.owner()); row.addProperty("name",entry.theme().name()); rows.add(row); }
        String selected=mode==ToolItem.Kind.SCAN?TemplateScanner.selected(player):selection(player,mode);
        root.add("rows",rows); root.addProperty("selected",selected);
        if(!selected.isEmpty() && state.entries.containsKey(selected)) send(player,"theme",selected,state.entries.get(selected).theme().bytes());
        send(player,open?"open":"catalog","",Pattern.JSON.toJson(root).getBytes(StandardCharsets.UTF_8));
    }
    public static void request(ServerPlayer player,TemplateProtocol.Request request) {
        int tick=player.server.getTickCount(); if(tick-LAST.getOrDefault(player.getUUID(),-100)<4) return; LAST.put(player.getUUID(),tick);
        if(request.operation().equals("import")) {
            if(!Settings.TEMPLATE_IMPORTS.get() || guard==null || !guard.allowed(player.getUUID())) { message(player,"Imports are disabled or blocked; contact an administrator."); return; }
            if(UPLOADS.size()>=64 || UPLOADS.stream().anyMatch(u -> u.player.equals(player.getUUID()))) { message(player,"An import is already pending; try again shortly."); return; }
            UPLOADS.add(new Upload(player.getUUID(),request));
        } else perform(player,request);
    }
    private static void perform(ServerPlayer player,TemplateProtocol.Request request) {
        try {
            var mode=heldMode(player); var state=TemplateState.get(player.server);
            switch(request.operation()) {
                case "delete" -> {
                    if(mode!=ToolItem.Kind.PAINT) throw new Pattern.Rejected("Use the Paint Tool to delete your themes");
                    delete(player,request.key()); message(player,"Theme deleted. Affected surfaces inherit their defaults."); open(player);
                }
                case "catalog" -> { String[] query=new String(request.data(),StandardCharsets.UTF_8).split("\n",2); if(request.data().length>512) throw new Pattern.Rejected("Search too long"); catalog(player,Integer.parseInt(request.key()),false,query.length>0?query[0]:"all",query.length>1?query[1]:""); }
                case "get" -> { var entry=requireEntry(state,request.key()); send(player,"theme",entry.id(),entry.theme().bytes()); }
                case "export" -> { requireScanner(mode); var entry=requireEntry(state,request.key()); send(player,"export",entry.id(),entry.theme().bytes()); }
                case "export_draft" -> { requireScanner(mode); send(player,"export","",TemplateScanner.draft(player).theme().bytes()); }
                case "new_scan","discard_new_scan" -> {
                    requireScanner(mode); TemplateScanner.create(player,new String(request.data(),StandardCharsets.UTF_8),request.operation().equals("discard_new_scan")); open(player);
                }
                case "import" -> {
                    requireScanner(mode); if(!Settings.TEMPLATE_IMPORTS.get()) throw new Pattern.Rejected("Player imports disabled");
                    checkOwnership(player,request.key());
                    var theme=guard.process(player.getUUID(),() -> { var candidate=Theme.parse(request.data()); candidate.validateMaterials(); return candidate; },
                            (incident,error) -> Elsebase.LOGGER.error("Theme import incident {} from UUID {}",incident,player.getUUID(),error));
                    store(player,request.key(),theme); message(player,"Theme imported."); catalog(player,0,false,"own","");
                }
                case "save_scan" -> {
                    requireScanner(mode); var draft=TemplateScanner.draft(player);
                    if(!request.key().equals(draft.base())) throw new Pattern.Rejected("Scan buffer changed; reopen the scanner menu");
                    var theme=draft.theme().named(new String(request.data(),StandardCharsets.UTF_8)); theme.validateMaterials();
                    String destination=!draft.base().isEmpty() && requireEntry(state,draft.base()).owner().equals(player.getUUID().toString())?draft.base():"";
                    String stored=store(player,destination,theme); TemplateScanner.saved(player,stored,theme);
                    message(player,destination.isEmpty()?"Saved your own theme copy.":"Theme updated everywhere it is used."); catalog(player,0,false,"own","");
                }
                case "select","discard_select" -> {
                    requireEntry(state,request.key());
                    if(mode==ToolItem.Kind.SCAN) TemplateScanner.select(player,request.key(),request.operation().equals("discard_select"));
                    else { state.choices.computeIfAbsent(player.getUUID(),id -> new EnumMap<>(ToolItem.Kind.class)).put(mode,request.key()); state.setDirty(); }
                    message(player,"Selected: "+state.entries.get(request.key()).theme().name()); send(player,"selected",request.key(),new byte[0]);
                }
                case "default" -> {
                    if(mode!=ToolItem.Kind.PAINT) throw new Pattern.Rejected("Use the Paint Tool to set your default theme");
                    if(!Settings.PERSONAL_TEMPLATES.get()) throw new Pattern.Rejected("Personal defaults disabled by server");
                    requireEntry(state,request.key()); WorldState.get(player.server).home(player.server,player.getUUID());
                    state.defaults.put(player.getUUID(),request.key()); state.changed(); message(player,"Default theme updated for your region.");
                }
                default -> throw new Pattern.Rejected("Unknown template operation");
            }
        } catch(Pattern.Rejected e) { message(player,e.getMessage()); }
        catch(NumberFormatException e) { message(player,"Invalid page number."); }
        catch(IOException e) { Elsebase.LOGGER.error("Theme storage failed; existing data retained",e); message(player,"Theme storage failed; contact the administrator. No player penalty."); }
    }
    /** Delete only owned definitions; file failure leaves all live bindings intact. Private drafts become unpublished copies. */
    public static void delete(ServerPlayer player,String id) throws IOException {
        var state=TemplateState.get(player.server); var entry=requireEntry(state,id);
        if(!entry.owner().equals(player.getUUID().toString()) || !id.matches("player/"+player.getUUID()+"/[0-9a-f-]{36}")) throw new Pattern.Rejected("Only your own themes can be deleted");
        Files.deleteIfExists(directory.resolve(id.replace('/','_')+".json"));
        state.entries.remove(id); state.defaults.values().removeIf(id::equals);
        state.bindings.values().forEach(map -> map.values().removeIf(id::equals)); state.bindings.values().removeIf(Map::isEmpty);
        state.choices.values().forEach(map -> map.values().removeIf(id::equals));
        state.scans.replaceAll((owner,draft) -> draft.base().equals(id)?new TemplateScanner.Draft("",draft.theme(),draft.changed()):draft);
        state.changed();
    }
    private static void requireScanner(ToolItem.Kind mode) { if(mode!=ToolItem.Kind.SCAN) throw new Pattern.Rejected("Use the Scan Tool for library operations"); }
    private static TemplateState.Entry requireEntry(TemplateState state,String id) { var entry=state.entries.get(id); if(entry==null) throw new Pattern.Rejected("Template no longer exists"); return entry; }
    private static void checkOwnership(ServerPlayer player,String id) {
        var state=TemplateState.get(player.server);
        if(!id.isEmpty() && !requireEntry(state,id).owner().equals(player.getUUID().toString())) throw new Pattern.Rejected("Only your own templates may be overwritten");
        if(id.isEmpty() && (state.entries.size()>=4096 || state.entries.values().stream().filter(e -> e.owner().equals(player.getUUID().toString())).count()>=Settings.TEMPLATE_QUOTA.get())) throw new Pattern.Rejected("Template quota reached");
    }
    private static String store(ServerPlayer player,String id,Theme theme) throws IOException {
        checkOwnership(player,id);
        final String replaced=id;
        if(TemplateState.get(player.server).entries.values().stream().anyMatch(e -> e.owner().equals(player.getUUID().toString()) && !e.id().equals(replaced) && e.theme().name().equalsIgnoreCase(theme.name()))) throw new Pattern.Rejected("You already have a template with this name. Rename it or explicitly overwrite your existing template.");
        if(id.isEmpty()) id="player/"+player.getUUID()+"/"+UUID.randomUUID();
        // Only server-generated identity determines the path; imported names never become paths.
        if(!id.matches("player/[0-9a-f-]{36}/[0-9a-f-]{36}")) throw new Pattern.Rejected("Invalid template ownership record");
        var target=directory.resolve(id.replace('/','_')+".json"); ImportGuard.writeAtomic(target,theme.bytes());
        var state=TemplateState.get(player.server); state.entries.put(id,new TemplateState.Entry(id,player.getUUID().toString(),theme)); state.changed(); return id;
    }
    /** Ordinary paint use changes only the appearance binding; holes and player construction are preserved. */
    public static void paint(ServerPlayer player) {
        try {
            if(heldMode(player)!=ToolItem.Kind.PAINT) throw new Pattern.Rejected("Hold the Paint Tool");
            String id=selection(player,ToolItem.Kind.PAINT);
            if(id.isEmpty()) { open(player); return; }
            if(!player.level().dimension().equals(Elsebase.DIMENSION)) throw new Pattern.Rejected("Room styling belongs in the Backdoor");
            var panel=PanelSelection.select(player.level(),player.position(),player.getEyePosition(),player.getLookAngle(),false);
            if(panel==null || !WorldEdits.authorizeAppearance(player,panel.positions(true))) throw new Pattern.Rejected("Surface is protected or out of reach");
            requireEntry(TemplateState.get(player.server),id);
            TemplateState.get(player.server).bind(panel,id); message(player,"Surface painted.");
        } catch(Pattern.Rejected failure) { message(player,failure.getMessage()); }
    }
    public static void restored(ServerPlayer player,StructuralEditor.Panel panel) {
        TemplateState.get(player.server).bind(panel,selection(player,ToolItem.Kind.CREATE));
    }
    public static Pattern.Material appearance(MinecraftServer server,BlockPos pos,Direction face) {
        var address=SurfaceAddress.at(pos,face); return patternAt(server,pos,face).at(address.u(),address.v());
    }
    public static Pattern patternAt(MinecraftServer server,BlockPos pos,Direction face) {
        var address=SurfaceAddress.at(pos,face); var state=TemplateState.get(server); String id=state.surface(server,pos.getX()>>4,pos.getZ()>>4,address.index());
        return state.pattern(id);
    }
    public static void reload(MinecraftServer server) {
        var state=TemplateState.get(server); var loaded=new HashMap<String,TemplateState.Entry>();
        try { Files.createDirectories(directory); try(var paths=Files.list(directory)) {
            for(var path:paths.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().limit(4097).toList()) {
                try {
                    if(loaded.size()>=4096 || Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path)>Pattern.MAX_BYTES) throw new Pattern.Rejected("Library limit or unsafe file");
                    String stem=path.getFileName().toString().replaceFirst("\\.json$",""); String id,owner="";
                    if(stem.matches("player_[0-9a-f-]{36}_[0-9a-f-]{36}")) { owner=stem.substring(7,43); id="player/"+owner+"/"+stem.substring(44); }
                    else { if(!stem.matches("[a-z0-9_-]{1,80}")) throw new Pattern.Rejected("Use simple ASCII library filenames"); id="pack/"+stem; }
                    var theme=Theme.readFile(path); theme.validateMaterials(); loaded.put(id,new TemplateState.Entry(id,owner,theme));
                } catch(IOException | RuntimeException e) { Elsebase.LOGGER.warn("Template library file rejected: {} ({})",path.getFileName(),e.getMessage()); }
            }
        } if(state.entries.size()+loaded.keySet().stream().filter(id -> !state.entries.containsKey(id)).count()>4096) throw new IOException("Template registry limit exceeded"); state.entries.putAll(loaded); state.changed();
        } catch(IOException e) { Elsebase.LOGGER.error("Cannot reload template library; world definitions retained",e); }
    }
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("elsebase").then(net.minecraft.commands.Commands.literal("templates").requires(s -> s.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("reload").executes(c -> { reload(c.getSource().getServer()); c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Template library reloaded; check server log for rejected files."),true); return 1; }))
            .then(net.minecraft.commands.Commands.literal("blocked").executes(c -> { c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(guard==null?"Unavailable":guard.entries().toString()),false); return 1; }))
            .then(net.minecraft.commands.Commands.literal("unlock").then(net.minecraft.commands.Commands.argument("uuid",net.minecraft.commands.arguments.UuidArgument.uuid()).executes(c -> {
                try { guard.unlock(net.minecraft.commands.arguments.UuidArgument.getUuid(c,"uuid")); c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Template imports unlocked."),true); return 1; }
                catch(IOException e) { c.getSource().sendFailure(net.minecraft.network.chat.Component.literal(e.getMessage())); return 0; }
            })))));
    }
}
