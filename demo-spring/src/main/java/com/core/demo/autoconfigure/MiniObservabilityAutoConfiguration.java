package com.core.demo.autoconfigure;

import com.core.demo.StompTracingChannelInterceptor;
import com.core.demo.TraceContextHandshakeInterceptor;
import com.core.demo.TracingClientInterceptor;
import com.core.demo.TracingFilter;
import com.core.demo.WebSocketSessionMetricsListener;
import com.core.export.ServiceIdentity;
import com.core.export.metrics.ConsoleMetricsExportSink;
import com.core.export.metrics.ElasticsearchMetricsExportSink;
import com.core.export.metrics.HttpMetricsExportSink;
import com.core.export.metrics.MetricsExportSink;
import com.core.export.metrics.MetricsPushExporter;
import com.core.export.metrics.PrometheusMetricsScrapeEndpoint;
import com.core.export.tracing.ConsoleSpanSink;
import com.core.export.tracing.ElasticsearchSpanSink;
import com.core.export.tracing.HttpSpanSink;
import com.core.export.tracing.SpanExporter;
import com.core.export.tracing.SpanSink;
import com.core.export.tracing.ZipkinSpanSink;
import com.core.metrics.MetricsConfig;
import com.core.metrics.MetricsRegistry;
import com.core.metrics.SimpleMetricsRegistry;
import com.core.tracing.Sampler.Sampler;
import com.core.tracing.SpanDispatcher;
import com.core.tracing.Tracer;
import com.core.tracing.handler.SpanHandler;
import com.core.tracing.propagation.Propagator;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@AutoConfiguration
@EnableConfigurationProperties(MiniObservabilityProperties.class)
@ConditionalOnProperty(prefix = "mini-observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MiniObservabilityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ServiceIdentity miniServiceIdentity(MiniObservabilityProperties properties, Environment environment) {
        String serviceName = firstText(properties.getServiceName(), environment.getProperty("spring.application.name"), "application");
        String instanceId = properties.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            return ServiceIdentity.create(serviceName);
        }
        return new ServiceIdentity(serviceName, instanceId);
    }

    @Bean
    @ConditionalOnMissingBean
    public Propagator miniPropagator() {
        return new Propagator();
    }

    @Bean
    @ConditionalOnMissingBean
    public Sampler miniSampler(MiniObservabilityProperties properties) {
        if (!properties.getTracing().isEnabled()) {
            return Sampler.NEVER_SAMPLE;
        }
        return Sampler.create(properties.getTracing().getSamplingRate());
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricsRegistry miniMetricsRegistry(MiniObservabilityProperties properties) {
        if (!properties.getMetrics().isEnabled()) {
            return new MetricsRegistry() {
            };
        }
        return new SimpleMetricsRegistry(new MetricsConfig(properties.getMetrics().getSlowThresholdMillis()));
    }

    @Bean
    @ConditionalOnMissingBean
    public SpanDispatcher miniSpanDispatcher(List<SpanHandler> handlers) {
        return new SpanDispatcher(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public Tracer miniTracer(SpanDispatcher spanDispatcher, Sampler sampler) {
        return new Tracer(spanDispatcher, sampler);
    }

    @Bean
    @ConditionalOnClass(FilterRegistrationBean.class)
    @ConditionalOnProperty(prefix = "mini-observability.instrumentation", name = "http-server", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TracingFilter> miniTracingFilter(Tracer tracer,
                                                                   Propagator propagator,
                                                                   MetricsRegistry metricsRegistry) {
        FilterRegistrationBean<TracingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TracingFilter(tracer, propagator, metricsRegistry));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnClass(RestTemplate.class)
    @ConditionalOnProperty(prefix = "mini-observability.instrumentation", name = "http-client", havingValue = "true", matchIfMissing = true)
    public RestTemplateCustomizer miniRestTemplateCustomizer(Tracer tracer,
                                                            Propagator propagator,
                                                            MetricsRegistry metricsRegistry) {
        return restTemplate -> restTemplate.getInterceptors()
                .add(new TracingClientInterceptor(tracer, propagator, metricsRegistry));
    }

    @Bean
    @ConditionalOnClass(DataSource.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "mini-observability.instrumentation", name = "jdbc", havingValue = "true", matchIfMissing = true)
    public static BeanPostProcessor miniJdbcTracingDataSourcePostProcessor(Tracer tracer,
                                                                          MetricsRegistry metricsRegistry,
                                                                          MiniObservabilityProperties properties) {
        return new JdbcTracingDataSourceBeanPostProcessor(
                tracer,
                metricsRegistry,
                blankToNull(properties.getInstrumentation().getDbSystem())
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "mini-observability.instrumentation", name = "websocket", havingValue = "true", matchIfMissing = true)
    public TraceContextHandshakeInterceptor miniTraceContextHandshakeInterceptor(Propagator propagator) {
        return new TraceContextHandshakeInterceptor(propagator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mini-observability.instrumentation", name = "websocket", havingValue = "true", matchIfMissing = true)
    public StompTracingChannelInterceptor miniStompTracingChannelInterceptor(Tracer tracer, Propagator propagator) {
        return new StompTracingChannelInterceptor(tracer, propagator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mini-observability.instrumentation", name = "websocket", havingValue = "true", matchIfMissing = true)
    public WebSocketSessionMetricsListener miniWebSocketSessionMetricsListener(MetricsRegistry metricsRegistry,
                                                                              MiniObservabilityProperties properties) {
        return new WebSocketSessionMetricsListener(
                metricsRegistry,
                firstText(properties.getInstrumentation().getWebsocketEndpoint(), "WS /ws/chat")
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "mini-observability.export.traces", name = "enabled", havingValue = "true")
    public SpanSink miniSpanSink(MiniObservabilityProperties properties) {
        MiniObservabilityProperties.Export.Traces traces = properties.getExport().getTraces();
        return switch (normalized(traces.getType())) {
            case "console" -> new ConsoleSpanSink();
            case "http" -> new HttpSpanSink(requiredEndpoint(traces.getEndpoint(), "trace http endpoint"));
            case "zipkin" -> new ZipkinSpanSink(requiredEndpoint(traces.getEndpoint(), "zipkin endpoint"));
            case "elasticsearch", "elastic" -> new ElasticsearchSpanSink(
                    requiredEndpoint(traces.getEndpoint(), "elasticsearch bulk endpoint"),
                    firstText(traces.getElasticsearchIndex(), "mini-spans")
            );
            default -> throw new IllegalArgumentException("unknown trace sink type: " + traces.getType());
        };
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnBean(SpanSink.class)
    @ConditionalOnProperty(prefix = "mini-observability.export.traces", name = "enabled", havingValue = "true")
    public SpanExporter miniSpanExporter(ServiceIdentity serviceIdentity,
                                         SpanSink spanSink,
                                         MiniObservabilityProperties properties) {
        MiniObservabilityProperties.Export.Traces traces = properties.getExport().getTraces();
        return SpanExporter.builder()
                .serviceIdentity(serviceIdentity)
                .spanSink(spanSink)
                .queueCapacity(traces.getQueueCapacity())
                .batchSize(traces.getBatchSize())
                .maxDelayMillis(traces.getMaxDelayMillis())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "mini-observability.export.metrics", name = "push-enabled", havingValue = "true")
    public MetricsExportSink miniMetricsExportSink(MiniObservabilityProperties properties) {
        MiniObservabilityProperties.Export.Metrics metrics = properties.getExport().getMetrics();
        return switch (normalized(metrics.getPushType())) {
            case "console" -> new ConsoleMetricsExportSink();
            case "http" -> new HttpMetricsExportSink(requiredEndpoint(metrics.getEndpoint(), "metrics http endpoint"));
            case "elasticsearch", "elastic" -> new ElasticsearchMetricsExportSink(
                    requiredEndpoint(metrics.getEndpoint(), "elasticsearch bulk endpoint"),
                    firstText(metrics.getElasticsearchIndex(), "mini-metrics")
            );
            default -> throw new IllegalArgumentException("unknown metrics sink type: " + metrics.getPushType());
        };
    }

    @Bean(destroyMethod = "shutdownNow")
    @ConditionalOnProperty(prefix = "mini-observability.export.metrics", name = "push-enabled", havingValue = "true")
    public ScheduledExecutorService miniMetricsExportScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mini-metrics-exporter");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnBean(MetricsExportSink.class)
    @ConditionalOnProperty(prefix = "mini-observability.export.metrics", name = "push-enabled", havingValue = "true")
    public MetricsPushExporter miniMetricsPushExporter(ServiceIdentity serviceIdentity,
                                                       MetricsRegistry metricsRegistry,
                                                       MetricsExportSink metricsExportSink,
                                                       ScheduledExecutorService miniMetricsExportScheduler,
                                                       MiniObservabilityProperties properties) {
        return MetricsPushExporter.builder()
                .serviceIdentity(serviceIdentity)
                .metricsRegistry(metricsRegistry)
                .metricsExportSink(metricsExportSink)
                .scheduler(miniMetricsExportScheduler)
                .intervalSeconds(properties.getExport().getMetrics().getPushIntervalSeconds())
                .build();
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "mini-observability.export.metrics", name = "prometheus-enabled", havingValue = "true")
    public PrometheusMetricsScrapeEndpoint miniPrometheusMetricsScrapeEndpoint(ServiceIdentity serviceIdentity,
                                                                              MetricsRegistry metricsRegistry,
                                                                              MiniObservabilityProperties properties) {
        MiniObservabilityProperties.Export.Metrics metrics = properties.getExport().getMetrics();
        return new PrometheusMetricsScrapeEndpoint(
                serviceIdentity,
                metricsRegistry,
                metrics.getPrometheusHost(),
                metrics.getPrometheusPort(),
                metrics.getPrometheusPath()
        );
    }

    private static String requiredEndpoint(String value, String name) {
        String text = blankToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        return text;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            String text = blankToNull(value);
            if (text != null) {
                return text;
            }
        }
        return "application";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalized(String value) {
        String text = firstText(value, "console");
        return text.toLowerCase(Locale.ROOT);
    }
}
