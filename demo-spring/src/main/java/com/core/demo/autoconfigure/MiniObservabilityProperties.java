package com.core.demo.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mini-observability")
public class MiniObservabilityProperties {
    private boolean enabled = true;
    private String serviceName = "application";
    private String instanceId;
    private final Tracing tracing = new Tracing();
    private final Metrics metrics = new Metrics();
    private final Instrumentation instrumentation = new Instrumentation();
    private final Export export = new Export();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Tracing getTracing() {
        return tracing;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public Export getExport() {
        return export;
    }

    public static class Tracing {
        private boolean enabled = true;
        private float samplingRate = 1.0f;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public float getSamplingRate() {
            return samplingRate;
        }

        public void setSamplingRate(float samplingRate) {
            this.samplingRate = samplingRate;
        }
    }

    public static class Metrics {
        private boolean enabled = true;
        private long slowThresholdMillis = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSlowThresholdMillis() {
            return slowThresholdMillis;
        }

        public void setSlowThresholdMillis(long slowThresholdMillis) {
            this.slowThresholdMillis = slowThresholdMillis;
        }
    }

    public static class Instrumentation {
        private boolean httpServer = true;
        private boolean httpClient = true;
        private boolean jdbc = true;
        private boolean websocket = true;
        private String websocketEndpoint = "WS /ws/chat";
        private String dbSystem;

        public boolean isHttpServer() {
            return httpServer;
        }

        public void setHttpServer(boolean httpServer) {
            this.httpServer = httpServer;
        }

        public boolean isHttpClient() {
            return httpClient;
        }

        public void setHttpClient(boolean httpClient) {
            this.httpClient = httpClient;
        }

        public boolean isJdbc() {
            return jdbc;
        }

        public void setJdbc(boolean jdbc) {
            this.jdbc = jdbc;
        }

        public boolean isWebsocket() {
            return websocket;
        }

        public void setWebsocket(boolean websocket) {
            this.websocket = websocket;
        }

        public String getWebsocketEndpoint() {
            return websocketEndpoint;
        }

        public void setWebsocketEndpoint(String websocketEndpoint) {
            this.websocketEndpoint = websocketEndpoint;
        }

        public String getDbSystem() {
            return dbSystem;
        }

        public void setDbSystem(String dbSystem) {
            this.dbSystem = dbSystem;
        }
    }

    public static class Export {
        private final Traces traces = new Traces();
        private final Metrics metrics = new Metrics();

        public Traces getTraces() {
            return traces;
        }

        public Metrics getMetrics() {
            return metrics;
        }

        public static class Traces {
            private boolean enabled;
            private String type = "console";
            private String endpoint;
            private String elasticsearchIndex = "mini-spans";
            private int queueCapacity = 1024;
            private int batchSize = 64;
            private long maxDelayMillis = 1_000;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }

            public String getElasticsearchIndex() {
                return elasticsearchIndex;
            }

            public void setElasticsearchIndex(String elasticsearchIndex) {
                this.elasticsearchIndex = elasticsearchIndex;
            }

            public int getQueueCapacity() {
                return queueCapacity;
            }

            public void setQueueCapacity(int queueCapacity) {
                this.queueCapacity = queueCapacity;
            }

            public int getBatchSize() {
                return batchSize;
            }

            public void setBatchSize(int batchSize) {
                this.batchSize = batchSize;
            }

            public long getMaxDelayMillis() {
                return maxDelayMillis;
            }

            public void setMaxDelayMillis(long maxDelayMillis) {
                this.maxDelayMillis = maxDelayMillis;
            }
        }

        public static class Metrics {
            private boolean pushEnabled;
            private String pushType = "console";
            private String endpoint;
            private String elasticsearchIndex = "mini-metrics";
            private long pushIntervalSeconds = 5;
            private boolean prometheusEnabled;
            private String prometheusHost = "0.0.0.0";
            private int prometheusPort = 9464;
            private String prometheusPath = "/metrics";

            public boolean isPushEnabled() {
                return pushEnabled;
            }

            public void setPushEnabled(boolean pushEnabled) {
                this.pushEnabled = pushEnabled;
            }

            public String getPushType() {
                return pushType;
            }

            public void setPushType(String pushType) {
                this.pushType = pushType;
            }

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }

            public String getElasticsearchIndex() {
                return elasticsearchIndex;
            }

            public void setElasticsearchIndex(String elasticsearchIndex) {
                this.elasticsearchIndex = elasticsearchIndex;
            }

            public long getPushIntervalSeconds() {
                return pushIntervalSeconds;
            }

            public void setPushIntervalSeconds(long pushIntervalSeconds) {
                this.pushIntervalSeconds = pushIntervalSeconds;
            }

            public boolean isPrometheusEnabled() {
                return prometheusEnabled;
            }

            public void setPrometheusEnabled(boolean prometheusEnabled) {
                this.prometheusEnabled = prometheusEnabled;
            }

            public String getPrometheusHost() {
                return prometheusHost;
            }

            public void setPrometheusHost(String prometheusHost) {
                this.prometheusHost = prometheusHost;
            }

            public int getPrometheusPort() {
                return prometheusPort;
            }

            public void setPrometheusPort(int prometheusPort) {
                this.prometheusPort = prometheusPort;
            }

            public String getPrometheusPath() {
                return prometheusPath;
            }

            public void setPrometheusPath(String prometheusPath) {
                this.prometheusPath = prometheusPath;
            }
        }
    }
}
