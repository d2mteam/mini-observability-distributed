package com.core.tracing;

import java.security.SecureRandom;
import java.util.HexFormat;

public class IdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of().withLowerCase();

    public static String newTraceId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);        // điền 16 byte ngẫu nhiên
        return HEX.formatHex(bytes);    // → 32 ký tự hex thường, không gạch
    }

    public static String newSpanId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);        // điền 8 byte ngẫu nhiên
        return HEX.formatHex(bytes);    // → 16 ký tự hex thường
    }
}
