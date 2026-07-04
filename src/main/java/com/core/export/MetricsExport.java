package com.core.export;

import com.core.metrics.MetricsSnapshot;
import lombok.Builder;

@Builder
public record MetricsExport(String serviceName,
                            String instanceId,
                            long capturedAtMillis,
                            MetricsSnapshot snapshot) {
}