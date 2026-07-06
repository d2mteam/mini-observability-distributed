package com.core.demo;

import com.core.metrics.MetricsRegistry;
import com.core.tracing.Span;
import com.core.tracing.Tracer;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class JdbcTracingQueryExecutionListener implements QueryExecutionListener {
    private final Tracer tracer;
    private final MetricsRegistry metrics;
    private final String dbSystem;
    private final ThreadLocal<JdbcSpanScope> current = new ThreadLocal<>();

    JdbcTracingQueryExecutionListener(Tracer tracer, MetricsRegistry metrics, String dbSystem) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.dbSystem = blankToNull(dbSystem);
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        String sql = firstSql(queryInfoList);
        String operation = sqlOperation(sql);
        Span span = tracer.nextSpan()
                .name("JDBC " + operation)
                .kind(Span.Kind.CLIENT)
                .tag("protocol", "jdbc")
                .tag("db.operation", operation);
        if (dbSystem != null) {
            span.tag("db.system", dbSystem);
        }
        if (!sql.isBlank()) {
            span.tag("db.statement", sql);
        }

        metrics.onRequestStart();
        current.set(new JdbcSpanScope(span, tracer.withSpanInScope(span)));
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        JdbcSpanScope scope = current.get();
        current.remove();
        if (scope == null) {
            return;
        }

        long durationMillis = Math.max(0, execInfo.getElapsedTime());
        boolean error = execInfo.getThrowable() != null;
        try {
            if (error) {
                scope.span().error(execInfo.getThrowable());
            }
            tracer.finishSpan(scope.span());
            metrics.onRequestEnd(scope.span().getName(), durationMillis, error, 0);
        } finally {
            scope.close();
        }
    }

    private static String firstSql(List<QueryInfo> queryInfoList) {
        if (queryInfoList == null || queryInfoList.isEmpty()) {
            return "";
        }
        String sql = queryInfoList.getFirst().getQuery();
        return sql == null ? "" : sql.trim();
    }

    private static String sqlOperation(String sql) {
        if (sql == null || sql.isBlank()) {
            return "SQL";
        }
        String[] parts = sql.trim().split("\\s+", 2);
        return parts[0].toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record JdbcSpanScope(Span span, Tracer.SpanInScope scope) implements AutoCloseable {
        @Override
        public void close() {
            scope.close();
        }
    }
}
