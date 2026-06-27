package com.app.internal;

import java.util.UUID;

public class IdGenerator {
    public static String newTraceId() {
        return UUID.randomUUID().toString();
    }

    public static String newSpanId() {
        return UUID.randomUUID().toString();
    }
}
