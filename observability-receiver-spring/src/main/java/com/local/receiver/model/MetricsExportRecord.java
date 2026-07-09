package com.local.receiver.model;

public record MetricsExportRecord(String serviceName,
                                  String instanceId,
                                  long capturedAtMillis,
                                  MetricsSnapshotRecord snapshot) {
    public MetricsExportRecord {
        snapshot = snapshot == null ? MetricsSnapshotRecord.empty() : snapshot;
    }
}
