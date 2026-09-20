package dev.elsebase.template;

import java.nio.file.*;
import java.util.*;

/** Regression checks exercise the production bounded parser and persistent import circuit breaker. */
public final class TemplateDomainTests {
    private static void require(boolean condition,String reason) { if(!condition) throw new AssertionError(reason); }
    private static void rejected(Runnable operation) { try { operation.run(); throw new AssertionError("Expected rejection"); } catch(Pattern.Rejected expected) {} }
    public static void run() throws Exception {
        var material=new Pattern.Material("minecraft:stone",Map.of());
        var pattern=new Pattern("Stone","Test",2,1,List.of(material,new Pattern.Material("minecraft:bricks",Map.of())),List.of(0,1));
        require(pattern.equals(Pattern.parse(pattern.bytes())),"Portable template round-trip");
        var theme=new Theme("Complete theme","Test",pattern,pattern,pattern);
        require(theme.equals(Theme.parse(theme.bytes())),"Complete theme round-trip retains all multi-block patterns");
        rejected(() -> Theme.parse(pattern.bytes()));
        rejected(() -> Theme.parse(new String(theme.bytes(),java.nio.charset.StandardCharsets.UTF_8).replace("\"format\":2","\"format\":1").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        require(pattern.at(-1,7).block().equals("minecraft:bricks"),"Negative tiled coordinates");
        rejected(() -> Pattern.parse(new byte[65537]));
        rejected(() -> Pattern.parse("{\"name\":\"one\",\"name\":\"two\"}".getBytes()));
        rejected(() -> Pattern.parse("{\"a\":[[[[[[[[[[1]]]]]]]]]]}".getBytes()));
        rejected(() -> Pattern.parse(new String(pattern.bytes()).replace("\"width\":2","\"width\":2.5").getBytes()));
        rejected(() -> Pattern.parse(new String(pattern.bytes()).replace("\"cells\":[0,1]","\"cells\":\"wrong type\"").getBytes()));
        rejected(() -> Pattern.parse(new String(pattern.bytes()).replace("\"cells\":[0,1]","\"cells\":[0,999]").getBytes()));
        rejected(() -> new Pattern.Material("../../outside",Map.of()));
        rejected(() -> new Pattern("Bad\nName","",1,1,List.of(material),List.of(0)));
        Path root=Files.createTempDirectory(Path.of("build"),"template-domain-"); var id=UUID.randomUUID(); var guard=new ImportGuard(root.resolve("blocks.txt"));
        require(guard.process(id,() -> pattern,(key,error) -> {})==pattern,"Valid import succeeds");
        rejected(() -> { try { guard.process(id,() -> Pattern.parse("no".getBytes()),(key,error) -> {}); } catch(java.io.IOException e) { throw new AssertionError(e); } });
        require(guard.allowed(id),"Ordinary validation does not ban");
        int[] reports={0};
        rejected(() -> { try { guard.process(id,() -> { throw new IllegalStateException("Injected processing failure"); },(key,error) -> reports[0]++); } catch(java.io.IOException e) { throw new AssertionError(e); } });
        require(!guard.allowed(id) && reports[0]==1,"Unexpected failure bans and logs once");
        var restarted=new ImportGuard(root.resolve("blocks.txt")); require(!restarted.allowed(id),"Ban survives restart");
        rejected(() -> { try { restarted.process(id,() -> { throw new AssertionError("Blocked input must not be processed"); },(key,error) -> {}); } catch(java.io.IOException e) { throw new AssertionError(e); } });
        restarted.unlock(id); require(new ImportGuard(root.resolve("blocks.txt")).allowed(id),"Admin unlock persists");
        Files.writeString(root.resolve("corrupt.txt"),"broken"); require(!new ImportGuard(root.resolve("corrupt.txt")).allowed(id),"Unreadable blocklist fails closed");
        Path impossible=root.resolve("directory"); Files.createDirectory(impossible); var failedStorage=new ImportGuard(impossible);
        require(!failedStorage.available(),"Storage problem closes imports without attributing a player incident");
        System.out.println("PASS: template format, bounds, duplicate keys, tiling, import ban persistence, unlock and fail-closed storage");
    }
    private TemplateDomainTests() {}
}
