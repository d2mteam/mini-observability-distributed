package com.core.export.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConsoleMetricsExportSink implements MetricsExportSink {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void send(String json) {
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
