package dev.elsebase.client.preview;

import dev.elsebase.Elsebase;
import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/** Optional public Iris API bridge; no shader internals, mixins or hard dependency. Unknown integrations fail closed. */
public final class ShaderCompatibility {
    private static boolean initialized, unsafe;
    private static Object api;
    private static Method active;
    private ShaderCompatibility() {}
    public static boolean activeShaders() {
        if(!initialized) {
            initialized=true;
            boolean installed=ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");
            if(installed) {
                for(String name : new String[]{"net.irisshaders.iris.api.v0.IrisApi","net.coderbot.iris.api.v0.IrisApi"}) {
                    try {
                        var type=Class.forName(name); api=type.getMethod("getInstance").invoke(null); active=type.getMethod("isShaderPackInUse"); break;
                    } catch(ClassNotFoundException ignored) {
                        // Older API namespace is tried only when a shader integration is installed.
                    } catch(ReflectiveOperationException | LinkageError e) { fail(e); break; }
                }
                if(api==null && !unsafe) fail(new IllegalStateException("Shader integration has no supported public Iris API"));
            }
        }
        if(unsafe) return true;
        try { return api!=null && (boolean)active.invoke(api); }
        catch(ReflectiveOperationException | LinkageError e) { fail(e); return true; }
    }
    private static void fail(Throwable failure) {
        dev.elsebase.preview.ModelQuarantine.rethrowFatal(failure);
        unsafe=true; Elsebase.LOGGER.warn("Portal live view disabled: shader API unavailable; normal portal surface retained",failure);
    }
}
