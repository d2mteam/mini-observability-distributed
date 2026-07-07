package com.core.demo.autoconfigure;

import com.core.demo.TracingClientInterceptor;
import com.core.export.ServiceIdentity;
import com.core.export.tracing.SpanExporter;
import com.core.metrics.MetricsRegistry;
import com.core.tracing.Tracer;
import com.core.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniObservabilityAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MiniObservabilityAutoConfiguration.class))
            .withPropertyValues(
                    "mini-observability.service-name=test-service",
                    "mini-observability.instance-id=test-instance"
            );

    @Test
    void createsCoreBeansByDefault() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("miniTracer"));
            assertTrue(context.containsBean("miniPropagator"));
            assertTrue(context.containsBean("miniMetricsRegistry"));
            assertTrue(context.containsBean("miniTracingFilter"));
            assertTrue(context.containsBean("miniRestTemplateCustomizer"));

            assertTrue(context.getBean(Tracer.class) instanceof Tracer);
            assertTrue(context.getBean(Propagator.class) instanceof Propagator);
            assertTrue(context.getBean(MetricsRegistry.class) instanceof MetricsRegistry);
            assertEquals(new ServiceIdentity("test-service", "test-instance"), context.getBean(ServiceIdentity.class));
        });
    }

    @Test
    void backsOffWhenDisabled() {
        contextRunner
                .withPropertyValues("mini-observability.enabled=false")
                .run(context -> assertFalse(context.containsBean("miniTracer")));
    }

    @Test
    void restTemplateCustomizerAddsTracingInterceptor() {
        contextRunner.run(context -> {
            RestTemplate restTemplate = new RestTemplate();
            context.getBean(RestTemplateCustomizer.class).customize(restTemplate);

            assertTrue(restTemplate.getInterceptors().stream()
                    .anyMatch(interceptor -> interceptor instanceof TracingClientInterceptor));
        });
    }

    @Test
    void traceExporterIsCreatedOnlyWhenTraceExportIsEnabled() {
        contextRunner.run(context -> assertFalse(context.containsBean("miniSpanExporter")));

        contextRunner
                .withPropertyValues("mini-observability.export.traces.enabled=true")
                .run(context -> assertTrue(context.getBean(SpanExporter.class) instanceof SpanExporter));
    }
}
