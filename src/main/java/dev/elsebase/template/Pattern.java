package dev.elsebase.template;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable, bounded interchange format. Contains no executable data, paths, NBT or nested references. */
public record Pattern(String name, String author, int width, int height, List<Material> palette, List<Integer> cells) {
    public static final int MAX_BYTES=65536;
    public static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    public static final class Rejected extends RuntimeException { public Rejected(String reason) { super(reason); } }
    public record Material(String block, Map<String,String> properties) {
        public Material {
            if(block==null || block.length()>128 || !block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new Rejected("Invalid material ID");
            properties=Map.copyOf(properties);
            if(properties.size()>16 || properties.entrySet().stream().anyMatch(e -> !e.getKey().matches("[a-z0-9_]{1,32}") || !e.getValue().matches("[a-z0-9_]{1,32}"))) throw new Rejected("Invalid material properties");
        }
    }
    public Pattern {
        name=text(name,64); author=text(author,64);
        if(name.isBlank() || width<1 || width>16 || height<1 || height>16) throw new Rejected("Patterns must be named and sized 1..16 by 1..16");
        palette=List.copyOf(palette); cells=List.copyOf(cells);
        if(palette.isEmpty() || palette.size()>256 || cells.size()!=width*height) throw new Rejected("Invalid palette or cell indices");
        for(int index:cells) if(index<0 || index>=palette.size()) throw new Rejected("Invalid palette index");
    }
    private static String text(String value,int limit) {
        if(value==null || value.length()>limit || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c==0x00a7)) throw new Rejected("Invalid name or author");
        return value;
    }
    public Material at(int u,int v) { return palette.get(cells.get(Math.floorMod(v,height)*width+Math.floorMod(u,width))); }
    public Pattern named(String newName) { return new Pattern(newName,author,width,height,palette,cells); }
    public byte[] bytes() { byte[] result=JSON.toJson(this).getBytes(StandardCharsets.UTF_8); if(result.length>MAX_BYTES) throw new Rejected("Pattern exceeds 64 KiB limit"); return result; }
    public static Pattern readFile(java.nio.file.Path path) throws IOException { try(var stream=java.nio.file.Files.newInputStream(path)) { return parse(stream.readNBytes(MAX_BYTES+1)); } }
    /** Strict bounded JSON parser rejects duplicate keys, excessive nesting and unknown fields as normal validation. */
    public static Pattern parse(byte[] bytes) {
        JsonObject root=object(bytes);
        try {
            fields(root,Set.of("name","author","width","height","palette","cells"));
            var materials=new ArrayList<Material>();
            for(var entry:root.getAsJsonArray("palette")) {
                var m=entry.getAsJsonObject(); fields(m,Set.of("block","properties")); var properties=new TreeMap<String,String>();
                m.getAsJsonObject("properties").entrySet().forEach(e -> properties.put(e.getKey(),string(e.getValue())));
                materials.add(new Material(string(m.get("block")),properties));
            }
            var cells=new ArrayList<Integer>(); for(var cell:root.getAsJsonArray("cells")) cells.add(integer(cell));
            return new Pattern(string(root.get("name")),string(root.get("author")),integer(root.get("width")),integer(root.get("height")),materials,cells);
        } catch(Rejected e) { throw e; }
        catch(IllegalStateException | IllegalArgumentException | ClassCastException | NullPointerException e) { throw new Rejected("Malformed template fields"); }
    }
    public static JsonObject object(byte[] bytes) {
        if(bytes.length==0 || bytes.length>MAX_BYTES) throw new Rejected("Input exceeds 64 KiB limit");
        try(var reader=new JsonReader(new StringReader(new String(bytes,StandardCharsets.UTF_8)))) {
            reader.setLenient(false);
            var element=read(reader,0,new int[]{0});
            if(reader.peek()!=JsonToken.END_DOCUMENT || !element.isJsonObject()) throw new Rejected("Expected one JSON object");
            return element.getAsJsonObject();
        } catch(IOException | IllegalStateException | NumberFormatException e) { throw new Rejected("Malformed JSON"); }
    }
    private static JsonElement read(JsonReader r,int depth,int[] nodes) throws IOException {
        if(depth>8 || ++nodes[0]>8192) throw new Rejected("JSON complexity limit exceeded");
        return switch(r.peek()) {
            case BEGIN_OBJECT -> { var value=new JsonObject(); r.beginObject(); while(r.hasNext()) { var key=r.nextName(); if(key.length()>128 || value.has(key)) throw new Rejected("Invalid or duplicate key"); value.add(key,read(r,depth+1,nodes)); } r.endObject(); yield value; }
            case BEGIN_ARRAY -> { var value=new JsonArray(); r.beginArray(); while(r.hasNext()) value.add(read(r,depth+1,nodes)); r.endArray(); yield value; }
            case STRING -> { var value=r.nextString(); if(value.length()>4096) throw new Rejected("String too long"); yield new JsonPrimitive(value); }
            case NUMBER -> { var value=r.nextString(); if(!value.matches("-?[0-9]{1,10}")) throw new Rejected("Expected bounded integer"); yield new JsonPrimitive(Long.parseLong(value)); }
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            default -> throw new Rejected("Unsupported JSON value");
        };
    }
    public static String string(JsonElement value) { if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new Rejected("Expected text"); return value.getAsString(); }
    public static int integer(JsonElement value) { if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new Rejected("Expected integer"); long n=value.getAsLong(); if(n<Integer.MIN_VALUE || n>Integer.MAX_VALUE) throw new Rejected("Integer out of range"); return (int)n; }
    public static void fields(JsonObject object,Set<String> fields) { if(!object.keySet().equals(fields)) throw new Rejected("Missing or unknown fields"); }
}
