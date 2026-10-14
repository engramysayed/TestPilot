package delivery.job.port;

import java.util.ServiceLoader;

/**
 * Resolves the in-process port implementations via {@link ServiceLoader} (no compile-time engine -> portal
 * dependency). keel-portal registers {@code delivery.portal.engine.InProcessEnginePorts}; keel-engine (B07)
 * ships its own provider. {@link #override} is for tests.
 */
public final class EnginePorts {
    private static volatile SiteMemoryPort override;
    private static volatile SiteMemoryPort loaded;

    private EnginePorts() {
    }

    public static SiteMemoryPort siteMemory() {
        SiteMemoryPort o = override;
        if (o != null) {
            return o;
        }
        SiteMemoryPort l = loaded;
        if (l == null) {
            synchronized (EnginePorts.class) {
                if (loaded == null) {
                    loaded = ServiceLoader.load(SiteMemoryPort.class, EnginePorts.class.getClassLoader())
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "No SiteMemoryPort provider on the classpath (META-INF/services)"));
                }
                l = loaded;
            }
        }
        return l;
    }

    /** Test hook; pass {@code null} to restore the ServiceLoader provider. */
    public static void override(SiteMemoryPort port) {
        override = port;
    }
}
