# Observability Receiver API

Backend demo cho phần Output API trong báo cáo. App chỉ nhận JSON từ `MetricsExport` và `SpanExport`, lưu in-memory bounded, rồi cung cấp API query/export đơn giản.

Run:

```bash
JAVA_HOME=/home/minato-one/.jdks/ms-21.0.11 ../gradlew bootRun
```

Ingest:

- `POST /api/ingest/metrics` hoặc alias cũ `POST /ingest/metrics`
- `POST /api/ingest/spans` hoặc alias cũ `POST /ingest/traces`

Query/export:

- `GET /api/stats`
- `GET /api/metrics?endpoint={route-or-destination}&side=all|server|client&limit=50`
- `GET /api/traces?endpoint={endpoint}&status=all|error|slow&protocol=http&limit=20`
- `GET /api/traces/{traceId}`
- `GET /api/export/json?endpoint={endpoint}&metricsLimit=20&traceLimit=5`
- `GET /api/export/ai-context?endpoint={endpoint}&traceLimit=2`
- `GET/PUT /api/config`
- `DELETE /api/records`

`/api/export/ai-context` trả về một lát cắt điều tra theo endpoint, không dump toàn bộ store:

- `endpoint`: endpoint đang hỏi.
- `primaryMetrics`: snapshot mới nhất của endpoint đó.
- `traceIds`: các trace liên quan được chọn ưu tiên theo lỗi/HTTP 4xx-5xx/retry-like.
- `errorPatterns`: nhóm lỗi suy ra từ span lỗi trong các trace đã chọn.
- `relatedMetrics`: metrics của dependency xuất hiện trong trace, ví dụ `server.address`, `db.system`, route server span.
- `traces`: raw `SpanExport` theo `traceId`, giữ dữ liệu gốc để copy vào LLM.

Ghi chú: backend này cố tình đơn giản để demo cấu trúc, không có database, auth, alerting hay query language đầy đủ.
