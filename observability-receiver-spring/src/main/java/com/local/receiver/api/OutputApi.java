package com.local.receiver.api;

import com.local.receiver.api.ApiModels.AiContextBlock;
import com.local.receiver.api.ApiModels.BackendStats;
import com.local.receiver.api.ApiModels.MetricsQueryResponse;
import com.local.receiver.api.ApiModels.StructuredExport;
import com.local.receiver.api.ApiModels.TraceListResponse;
import com.local.receiver.api.ApiModels.TraceView;
import com.local.receiver.service.ExportService;
import com.local.receiver.service.MetricsQueryService;
import com.local.receiver.service.TraceQueryService;
import com.local.receiver.store.MetricsRecordStore;
import com.local.receiver.store.TraceRecordStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OutputApi {
    private final MetricsRecordStore metricsStore;
    private final TraceRecordStore traceStore;
    private final MetricsQueryService metricsQueryService;
    private final TraceQueryService traceQueryService;
    private final ExportService exportService;

    public OutputApi(MetricsRecordStore metricsStore,
                     TraceRecordStore traceStore,
                     MetricsQueryService metricsQueryService,
                     TraceQueryService traceQueryService,
                     ExportService exportService) {
        this.metricsStore = metricsStore;
        this.traceStore = traceStore;
        this.metricsQueryService = metricsQueryService;
        this.traceQueryService = traceQueryService;
        this.exportService = exportService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/stats")
    public BackendStats stats() {
        return new BackendStats(metricsStore.stats(), traceStore.stats());
    }

    @GetMapping("/api/metrics")
    public MetricsQueryResponse metrics(@RequestParam(required = false) String endpoint,
                                        @RequestParam(defaultValue = "all") String side,
                                        @RequestParam(defaultValue = "0") long fromMillis,
                                        @RequestParam(defaultValue = "0") long toMillis,
                                        @RequestParam(defaultValue = "50") int limit) {
        return metricsQueryService.query(endpoint, side, fromMillis, toMillis, limit);
    }

    @GetMapping("/api/traces")
    public TraceListResponse traces(@RequestParam(required = false) String endpoint,
                                    @RequestParam(defaultValue = "all") String status,
                                    @RequestParam(required = false) String protocol,
                                    @RequestParam(defaultValue = "0") long minDurationMillis,
                                    @RequestParam(defaultValue = "20") int limit) {
        return traceQueryService.list(endpoint, status, protocol, minDurationMillis, limit);
    }

    @GetMapping("/api/traces/{traceId}")
    public ResponseEntity<TraceView> trace(@PathVariable String traceId) {
        TraceView trace = traceQueryService.find(traceId);
        return trace == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(trace);
    }

    @GetMapping("/api/export/json")
    public StructuredExport structuredExport(@RequestParam(required = false) String endpoint,
                                             @RequestParam(defaultValue = "20") int metricsLimit,
                                             @RequestParam(defaultValue = "5") int traceLimit) {
        return exportService.structuredJson(endpoint, metricsLimit, traceLimit);
    }

    @GetMapping("/api/export/ai-context")
    public AiContextBlock aiContext(@RequestParam String endpoint,
                                    @RequestParam(defaultValue = "2") int traceLimit) {
        return exportService.aiContext(endpoint, traceLimit);
    }

    @DeleteMapping("/api/records")
    public ResponseEntity<Void> clear() {
        metricsStore.clear();
        traceStore.clear();
        return ResponseEntity.noContent().build();
    }
}
