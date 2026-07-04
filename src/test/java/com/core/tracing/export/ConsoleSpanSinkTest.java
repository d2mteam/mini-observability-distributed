package com.core.tracing.export;

import com.core.export.tracing.ConsoleSpanSink;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsoleSpanSinkTest {

    @Test
    void printsJsonToConfiguredStream() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8);
        ConsoleSpanSink sink = new ConsoleSpanSink(printStream);

        sink.send("{\"spans\":[]}");

        assertEquals("{\"spans\":[]}" + System.lineSeparator(),
                output.toString(StandardCharsets.UTF_8));
    }
}
