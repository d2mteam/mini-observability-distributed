package com.core.export.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.PrintStream;
import java.util.Objects;

public class ConsoleSpanSink implements SpanSink {
    private final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private final PrintStream out;

    public ConsoleSpanSink() {
        this(System.out);
    }

    public ConsoleSpanSink(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void send(SpanExport spanExport) throws Exception {
        out.println(writer.writeValueAsString(Objects.requireNonNull(spanExport, "spanExport")));
    }
}
