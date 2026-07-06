package com.core.tracing.export;

import com.core.export.tracing.ConsoleSpanSink;
import com.core.export.tracing.SpanExport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleSpanSinkTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void serializesAndPrintsJsonToConfiguredStream() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8);
        ConsoleSpanSink sink = new ConsoleSpanSink(printStream);

        sink.send(new SpanExport("orders", "instance-1", 123, List.of()));

        String json = output.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains(System.lineSeparator()), "expected pretty JSON");
        JsonNode node = MAPPER.readTree(json);
        assertEquals("orders", node.get("serviceName").asText());
        assertEquals("instance-1", node.get("instanceId").asText());
        assertEquals(123, node.get("capturedAtMillis").asLong());
        assertEquals(0, node.get("spans").size());
    }
}
