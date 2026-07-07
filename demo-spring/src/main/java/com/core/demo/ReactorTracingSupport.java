package com.core.demo;

import io.micrometer.context.ContextRegistry;
import reactor.core.publisher.Hooks;

public final class ReactorTracingSupport {
    private static final Object LOCK = new Object();
    private static volatile boolean installed;

    private ReactorTracingSupport() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        synchronized (LOCK) {
            if (installed) {
                return;
            }
            ContextRegistry.getInstance()
                    .registerThreadLocalAccessor(new TraceContextThreadLocalAccessor());
            Hooks.enableAutomaticContextPropagation();
            installed = true;
        }
    }
}
