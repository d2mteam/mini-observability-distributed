package com.core.export.tracing;

import java.io.PrintStream;
import java.util.Objects;

public class ConsoleSpanSink implements SpanSink {
    private final PrintStream out;

    public ConsoleSpanSink() {
        this(System.out);
    }

    public ConsoleSpanSink(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void send(String json) {
        out.println(json);
    }
}
