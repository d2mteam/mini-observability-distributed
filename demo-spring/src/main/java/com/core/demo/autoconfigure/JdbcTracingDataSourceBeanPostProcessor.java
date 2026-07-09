package com.core.demo.autoconfigure;

import com.core.demo.JdbcTracingDataSource;
import com.core.metrics.MetricsRegistry;
import com.core.tracing.Tracer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

public class JdbcTracingDataSourceBeanPostProcessor implements BeanPostProcessor {
    private final Tracer tracer;
    private final MetricsRegistry metricsRegistry;
    private final String dbSystem;

    public JdbcTracingDataSourceBeanPostProcessor(Tracer tracer,
                                                  MetricsRegistry metricsRegistry,
                                                  String dbSystem) {
        this.tracer = tracer;
        this.metricsRegistry = metricsRegistry;
        this.dbSystem = dbSystem;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource) {
            return JdbcTracingDataSource.wrap(dataSource, tracer, metricsRegistry, dbSystem);
        }
        return bean;
    }
}
