package com.core.demo.jdbc;

import com.core.metrics.MetricsRegistry;
import com.core.tracing.Tracer;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import javax.sql.DataSource;
import java.util.Objects;

public final class JdbcTracingDataSource {
    private JdbcTracingDataSource() {
    }

    public static DataSource wrap(DataSource dataSource, Tracer tracer, MetricsRegistry metrics) {
        return wrap(dataSource, tracer, metrics, null);
    }

    public static DataSource wrap(DataSource dataSource, Tracer tracer, MetricsRegistry metrics, String dbSystem) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(tracer, "tracer");
        Objects.requireNonNull(metrics, "metrics");
        return ProxyDataSourceBuilder
                .create(dataSource)
                .name("jdbc")
                .listener(new JdbcTracingQueryExecutionListener(tracer, metrics, dbSystem))
                .build();
    }
}
