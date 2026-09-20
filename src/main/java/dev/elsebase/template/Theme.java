package dev.elsebase.template;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.core.Direction;

/** One portable named design containing all three surfaces; patterns are internal, never separate library entries. */
public record Theme(String name, String author, Pattern wall, Pattern floor, Pattern ceiling) {
    public Theme {
        // Reuse the pattern text contract rather than accepting another naming format.
        wall = wall.named(name);
        new Pattern(name, author, 1, 1, wall.palette().subList(0, 1), java.util.List.of(0));
        java.util.Objects.requireNonNull(floor);
        java.util.Objects.requireNonNull(ceiling);
    }
    public Pattern face(Direction side) { return side==Direction.UP?ceiling:side==Direction.DOWN?floor:wall; }
    public Theme withFace(Direction side, Pattern pattern) {
        return new Theme(name,author,side.getAxis()!=Direction.Axis.Y?pattern:wall,side==Direction.DOWN?pattern:floor,side==Direction.UP?pattern:ceiling);
    }
    public Theme named(String value) { return new Theme(value,author,wall,floor,ceiling); }
    public byte[] bytes() {
        var root=new com.google.gson.JsonObject(); root.addProperty("format",2); root.addProperty("name",name); root.addProperty("author",author);
        root.add("wall",Pattern.JSON.toJsonTree(wall)); root.add("floor",Pattern.JSON.toJsonTree(floor)); root.add("ceiling",Pattern.JSON.toJsonTree(ceiling));
        byte[] bytes=Pattern.JSON.toJson(root).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(bytes.length>Pattern.MAX_BYTES) throw new Pattern.Rejected("Complete theme exceeds 64 KiB limit");
        return bytes;
    }
    public static Theme parse(byte[] bytes) {
        var root=Pattern.object(bytes); Pattern.fields(root,Set.of("format","name","author","wall","floor","ceiling"));
        if(Pattern.integer(root.get("format"))!=2) throw new Pattern.Rejected("Unsupported theme format; use a complete theme exported by this version");
        return new Theme(Pattern.string(root.get("name")),Pattern.string(root.get("author")),part(root,"wall"),part(root,"floor"),part(root,"ceiling"));
    }
    private static Pattern part(com.google.gson.JsonObject root,String key) { return Pattern.parse(Pattern.JSON.toJson(root.get(key)).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    public static Theme readFile(Path path) throws IOException { try(var stream=java.nio.file.Files.newInputStream(path)) { return parse(stream.readNBytes(Pattern.MAX_BYTES+1)); } }
    public void validateMaterials() { Materials.validate(wall); Materials.validate(floor); Materials.validate(ceiling); }
    public static String role(Direction side) { return side==Direction.UP?"ceiling":side==Direction.DOWN?"floor":"wall"; }
}
