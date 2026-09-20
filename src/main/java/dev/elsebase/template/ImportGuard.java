package dev.elsebase.template;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Server-installation UUID blocklist. Corruption/storage failure closes uploads, never silently resets bans. */
public final class ImportGuard {
    private final Path path;
    private final Map<UUID,String> blocked=new TreeMap<>();
    private boolean available=true;
    public ImportGuard(Path path) {
        this.path=path;
        if(Files.exists(path)) try {
            if(Files.size(path)>4*1024*1024) throw new IOException("Import blocklist too large");
            for(String line:Files.readAllLines(path)) { var split=line.split(" ",2); if(split.length!=2 || blocked.put(UUID.fromString(split[0]),split[1])!=null) throw new IOException("Malformed blocklist"); }
        } catch(IOException | RuntimeException e) { available=false; }
    }
    public boolean allowed(UUID player) { return available && !blocked.containsKey(player); }
    public boolean available() { return available; }
    public Map<UUID,String> entries() { return Map.copyOf(blocked); }
    /** Only processor failures are attributable. Persistence is deliberately outside this boundary. */
    public <T> T process(UUID player,Supplier<T> processor,BiConsumer<String,Throwable> reporter) throws IOException {
        if(!allowed(player)) throw new Pattern.Rejected("Imports blocked; ask an administrator to unlock your UUID");
        try { return processor.get(); }
        catch(Pattern.Rejected expected) { throw expected; }
        catch(RuntimeException | LinkageError failure) {
            dev.elsebase.preview.ModelQuarantine.rethrowFatal(failure);
            String incident=UUID.randomUUID().toString(); blocked.put(player,incident); reporter.accept(incident,failure);
            try { persist(); } catch(IOException storage) { available=false; storage.addSuppressed(failure); throw storage; }
            throw new Pattern.Rejected("Import failed. Imports blocked until administrator unlock. Incident: "+incident);
        }
    }
    public void unlock(UUID player) throws IOException {
        if(!available) throw new IOException("Repair the import blocklist and restart before unlocking");
        var old=blocked.remove(player); try { persist(); } catch(IOException e) { if(old!=null) blocked.put(player,old); available=false; throw e; }
    }
    private void persist() throws IOException {
        StringBuilder text=new StringBuilder(); blocked.forEach((id,incident) -> text.append(id).append(' ').append(incident).append('\n'));
        writeAtomic(path,text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static void writeAtomic(Path path,byte[] content) throws IOException {
        Files.createDirectories(path.getParent()); var temporary=Files.createTempFile(path.getParent(),"elsebase-",".tmp");
        try { Files.write(temporary,content); Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temporary); }
    }
}
