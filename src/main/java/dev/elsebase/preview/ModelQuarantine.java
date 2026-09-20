package dev.elsebase.preview;

import java.util.*;
import java.util.function.*;

/** Session-only circuit breaker for third-party models. Fatal JVM failures are never disguised as bad models. */
public final class ModelQuarantine<K> {
    private final Set<K> failed = new HashSet<>();
    private final BiConsumer<K,Throwable> reporter;
    public ModelQuarantine(BiConsumer<K,Throwable> reporter) { this.reporter=reporter; }
    public boolean contains(K key) { return failed.contains(key); }
    public int size() { return failed.size(); }
    public void clear() { failed.clear(); }
    /** The caller must commit geometry only after success, so partially emitted vertices are discarded. */
    public <T> T attempt(K key, Supplier<T> model) {
        if(failed.contains(key)) return null;
        try { return model.get(); }
        catch(RuntimeException | LinkageError failure) {
            rethrowFatal(failure);
            failed.add(key); reporter.accept(key,failure); return null;
        }
    }
    @SuppressWarnings("removal")
    public static void rethrowFatal(Throwable failure) {
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=failure;cause!=null && seen.add(cause);cause=cause.getCause()) {
            if(cause instanceof VirtualMachineError fatal) throw fatal;
            if(cause instanceof ThreadDeath fatal) throw fatal;
        }
    }
}
