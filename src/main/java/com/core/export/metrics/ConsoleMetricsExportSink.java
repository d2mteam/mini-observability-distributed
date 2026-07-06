package com.core.export.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class ConsoleMetricsExportSink implements MetricsExportSink {
    private final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

    @Override
    public void send(MetricsExport metricsExport) throws Exception {
        System.out.println(writer.writeValueAsString(metricsExport));
    }
}
