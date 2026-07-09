# demo-spring Auto Configuration

Module này cung cấp Spring Boot auto-configuration cho mini observability library. App Spring Boot chỉ cần thêm dependency tới `demo-spring`; Spring Boot sẽ đọc file auto-configuration imports và tự tạo các bean quan sát cơ bản.

Ví dụ YAML:

```yaml
mini-observability:
  service-name: chat-gateway
  instance-id: chat-gateway-1

  tracing:
    enabled: true
    sampling-rate: 1.0

  metrics:
    enabled: true
    slow-threshold-millis: 500

  instrumentation:
    http-server: true
    http-client: true
    jdbc: true
    websocket: true
    websocket-endpoint: WS /ws/chat
    db-system: postgresql

  export:
    traces:
      enabled: true
      type: zipkin
      endpoint: http://localhost:9411/api/v2/spans
    metrics:
      prometheus-enabled: true
      prometheus-port: 9464
      prometheus-path: /metrics
```

Tự đăng ký:

- Core beans: `ServiceIdentity`, `Propagator`, `Sampler`, `Tracer`, `MetricsRegistry`.
- HTTP server: `TracingFilter`.
- HTTP client: `RestTemplateCustomizer` thêm `TracingClientInterceptor`.
- JDBC: tự bọc `DataSource` bằng datasource-proxy.
- Export: `SpanExporter`, trace sink, metrics push exporter, hoặc Prometheus scrape endpoint.

WebSocket/STOMP tạo sẵn bean `TraceContextHandshakeInterceptor`, `StompTracingChannelInterceptor`, `WebSocketSessionMetricsListener`; app vẫn cần gắn interceptor vào STOMP broker config của chính nó.

Đây là auto-config mức demo: không có retry/backoff/auth/lifecycle manager phức tạp.
