package com.local.receiver.api;

import com.local.receiver.api.ApiModels.IngestResponse;
import com.local.receiver.model.MetricsExportRecord;
import com.local.receiver.model.SpanExportRecord;
import com.local.receiver.store.MetricsRecordStore;
import com.local.receiver.store.TraceRecordStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RecordIngestApi {
    private final MetricsRecordStore metricsStore;
    private final TraceRecordStore traceStore;

    public RecordIngestApi(MetricsRecordStore metricsStore, TraceRecordStore traceStore) {
        this.metricsStore = metricsStore;
        this.traceStore = traceStore;
    }

    @PostMapping({"/api/ingest/metrics", "/ingest/metrics"})
    public ResponseEntity<IngestResponse> ingestMetrics(@RequestBody MetricsExportRecord export) {
        metricsStore.append(export);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new IngestResponse(true, "metrics", 1, 0, List.of()));
    }

    @PostMapping({"/api/ingest/spans", "/api/ingest/traces", "/ingest/spans", "/ingest/traces"})
    public ResponseEntity<IngestResponse> ingestSpans(@RequestBody SpanExportRecord export) {
        traceStore.append(export);
        List<String> traceIds = export.spans().stream()
                .map(span -> span.traceId())
                .filter(traceId -> traceId != null && !traceId.isBlank())
                .distinct()
                .toList();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new IngestResponse(true, "spans", 1, export.spans().size(), traceIds));
    }
}
