package dev.elsebase.template;

import dev.elsebase.*;
import dev.elsebase.portal.WorldState;
import dev.elsebase.structure.StructuralEditor.Panel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-contained definitions and sparse face bindings; installation library files are portable, not required to reopen a save. */
public final class TemplateState extends SavedData {
    public record Entry(String id,String owner,Theme theme) {}
    public static final String[] THEMES={"quiet_workshop","arcane_archive","verdant_cloister","astral_observatory","deepstone_halls","porcelain_sanctuary","service_layer"};
    public final Map<String,Entry> entries=new TreeMap<>();
    public final Map<UUID,String> defaults=new HashMap<>();
    public final Map<UUID,EnumMap<ToolItem.Kind,String>> choices=new HashMap<>();
    public final Map<UUID,TemplateScanner.Draft> scans=new HashMap<>();
    public final Map<Long,Map<Integer,String>> bindings=new HashMap<>();
    private final Map<Long,UUID> regions=new HashMap<>();
    private int homeCount=-1;
    public long generation;
    public TemplateState() {
        for(String theme:THEMES) {
            String id=builtin(theme);
            String name=Arrays.stream(theme.split("_")).map(word -> Character.toUpperCase(word.charAt(0))+word.substring(1)).collect(java.util.stream.Collectors.joining(" "));
            entries.put(id,new Entry(id,"",BuiltinThemes.create(theme,name)));
        }
    }
    public static String builtin(String theme) { return "builtin/"+theme; }
    /** Rendering keys are internal role selectors, never independent library identities. */
    public Pattern pattern(String key) {
        int split=key.lastIndexOf('#'); if(split<0) throw new IllegalArgumentException("Missing theme surface");
        var theme=entries.get(key.substring(0,split)).theme();
        return switch(key.substring(split+1)) { case "wall" -> theme.wall(); case "floor" -> theme.floor(); case "ceiling" -> theme.ceiling(); default -> throw new IllegalArgumentException("Invalid theme surface"); };
    }
    public static TemplateState get(MinecraftServer server) {
        var storage=server.overworld().getDataStorage(); var factory=new Factory<>(TemplateState::new,TemplateState::load);
        var value=storage.get(factory,"elsebase_templates"); if(value!=null) return value;
        if(java.nio.file.Files.exists(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/elsebase_templates.dat"))) throw new IllegalStateException("Cannot load Elsebase template save; restore a valid backup");
        value=new TemplateState(); storage.set("elsebase_templates",value); return value;
    }
    public void changed() { generation++; setDirty(); }
    public void bind(Panel panel,String id) {
        long chunk=net.minecraft.world.level.ChunkPos.asLong(panel.cellX(),panel.cellZ()); int index=panel.floor()/8*6+panel.side().ordinal();
        if(id.isEmpty()) { var map=bindings.get(chunk); if(map!=null) { map.remove(index); if(map.isEmpty()) bindings.remove(chunk); } }
        else bindings.computeIfAbsent(chunk,key -> new HashMap<>()).put(index,id);
        changed();
    }
    /** Constant-time slot-grid lookup after rebuilding only when a home is added/removed. No online-player dependency. */
    public UUID owner(MinecraftServer server,int chunkX,int chunkZ) {
        var world=WorldState.get(server); int spacing=world.allocationSpacing();
        if(homeCount!=world.homes.size()) {
            regions.clear(); world.homes.forEach((id,home) -> regions.put(net.minecraft.world.level.ChunkPos.asLong(Math.floorDiv(home.slot().x(),spacing),Math.floorDiv(home.slot().z(),spacing)),id)); homeCount=world.homes.size();
        }
        int regionX=Math.floorDiv(chunkX+spacing/32,spacing/16),regionZ=Math.floorDiv(chunkZ+spacing/32,spacing/16);
        return regions.get(net.minecraft.world.level.ChunkPos.asLong(regionX,regionZ));
    }
    public String[] column(MinecraftServer server,int x,int z) {
        String global=TemplateServer.globalStyle();
        String style=Settings.PERSONAL_TEMPLATES.get()?defaults.getOrDefault(owner(server,x,z),global):global;
        var overrides=bindings.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x,z),Map.of()); var result=new String[96];
        for(int i=0;i<96;i++) { var side=Direction.values()[i%6]; String id=overrides.getOrDefault(i,style);
            if(!entries.containsKey(id)) id=global; if(!entries.containsKey(id)) id=builtin("quiet_workshop"); result[i]=id+"#"+Theme.role(side); }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("format",2); var definitions=new ListTag();
        entries.values().stream().filter(e -> !e.id.startsWith("builtin/")).forEach(e -> { var row=new CompoundTag(); row.putString("id",e.id); row.putString("owner",e.owner); row.putByteArray("theme",e.theme.bytes()); definitions.add(row); }); tag.put("definitions",definitions);
        var choices=new ListTag(); defaults.forEach((id,theme) -> { var row=new CompoundTag(); row.putUUID("owner",id); row.putString("theme",theme); choices.add(row); }); tag.put("defaults",choices);
        var faces=new ListTag(); bindings.forEach((chunk,values) -> { var row=new CompoundTag(); row.putLong("chunk",chunk); values.forEach((index,id) -> row.putString("f"+index,id)); faces.add(row); }); tag.put("bindings",faces);
        var tools=new ListTag(); this.choices.forEach((owner,selected) -> { var row=new CompoundTag(); row.putUUID("owner",owner); selected.forEach((kind,id) -> row.putString(kind.name(),id)); tools.add(row); }); tag.put("tools",tools);
        var buffers=new ListTag(); scans.forEach((owner,draft) -> { var row=new CompoundTag(); row.putUUID("owner",owner); row.putString("base",draft.base()); row.putByteArray("theme",draft.theme().bytes()); row.putString("changed",String.join(",",new TreeSet<>(draft.changed()))); buffers.add(row); }); tag.put("buffers",buffers);
        return tag;
    }
    public static TemplateState load(CompoundTag tag,HolderLookup.Provider registries) {
        if(tag.getInt("format")!=2) throw new IllegalStateException("Unsupported template save format; create a new development test world"); var value=new TemplateState();
        for(var item:tag.getList("definitions",Tag.TAG_COMPOUND)) { var row=(CompoundTag)item; String id=row.getString("id"); if(id.startsWith("builtin/") || id.contains("#") || id.length()>120 || value.entries.containsKey(id)) throw new IllegalStateException("Invalid template identity"); value.entries.put(id,new Entry(id,row.getString("owner"),Theme.parse(row.getByteArray("theme")))); }
        if(value.entries.size()>4096) throw new IllegalStateException("Template registry exceeds limit");
        for(var item:tag.getList("defaults",Tag.TAG_COMPOUND)) { var row=(CompoundTag)item; value.defaults.put(row.getUUID("owner"),row.getString("theme")); }
        for(var item:tag.getList("bindings",Tag.TAG_COMPOUND)) { var row=(CompoundTag)item; var map=new HashMap<Integer,String>(); for(String key:row.getAllKeys()) if(key.startsWith("f")) { int index=Integer.parseInt(key.substring(1)); if(index<0 || index>=96) throw new IllegalStateException("Invalid face index"); map.put(index,row.getString(key)); } value.bindings.put(row.getLong("chunk"),map); }
        for(var item:tag.getList("tools",Tag.TAG_COMPOUND)) { var row=(CompoundTag)item; var selected=new EnumMap<ToolItem.Kind,String>(ToolItem.Kind.class); for(var kind:List.of(ToolItem.Kind.CREATE,ToolItem.Kind.PAINT)) if(row.contains(kind.name())) selected.put(kind,row.getString(kind.name())); value.choices.put(row.getUUID("owner"),selected); }
        for(var item:tag.getList("buffers",Tag.TAG_COMPOUND)) { var row=(CompoundTag)item; String changed=row.getString("changed"); var roles=changed.isEmpty()?Set.<String>of():Set.copyOf(Arrays.asList(changed.split(","))); if(!Set.of("wall","floor","ceiling").containsAll(roles)) throw new IllegalStateException("Invalid scan roles"); value.scans.put(row.getUUID("owner"),new TemplateScanner.Draft(row.getString("base"),Theme.parse(row.getByteArray("theme")),roles)); }
        return value;
    }
}
