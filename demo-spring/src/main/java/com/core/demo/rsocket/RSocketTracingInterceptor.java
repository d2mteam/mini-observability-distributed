package com.core.demo.rsocket;

import com.core.metrics.MetricsRegistry;
import com.core.tracing.Span;
import com.core.tracing.Tracer;
import com.core.tracing.propagation.Propagator;
import com.core.tracing.propagation.TraceContext;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.util.RSocketProxy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.util.context.ContextView;

public final class RSocketTracingInterceptor implements RSocketInterceptor {
    public enum Direction {REQUESTER, RESPONDER}

    private final Tracer tracer;
    private final Propagator propagator;
    private final MetricsRegistry metrics;
    private final Direction direction;
    private final String remoteName;

    public RSocketTracingInterceptor(Tracer tracer,
                                     Propagator propagator,
                                     MetricsRegistry metrics,
                                     Direction direction,
                                     String remoteName) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.metrics = metrics;
        this.direction = direction;
        this.remoteName = remoteName == null || remoteName.isBlank() ? "rsocket" : remoteName;
    }

    @Override
    public RSocket apply(RSocket rSocket) {
        return new TracingRSocket(rSocket);
    }

    private Interaction startClient(Payload payload, ContextView view) {
        int bytes = payload.data().readableBytes();
        String route = RSocketTracePropagation.routeOf(payload);
        TraceContext current = view.hasKey(TraceContextThreadLocalAccessor.KEY)
                ? view.get(TraceContextThreadLocalAccessor.KEY)
                : null;
        Span span = tracer.nextSpan(current)
                .name("RSOCKET " + route)
                .kind(Span.Kind.CLIENT)
                .tag("protocol", "rsocket")
                .tag("rsocket.role", "requester")
                .tag("rsocket.route", route)
                .tag("server.address", remoteName)
                .tag("messaging.message.payload_size_bytes", String.valueOf(bytes));
        metrics.onClientCallStart();
        TraceContext context = contextOf(span);
        Payload outbound = RSocketTracePropagation.inject(propagator, context, payload);
        return new Interaction(span, context, outbound, route, bytes);
    }

    private Interaction startServer(Payload payload) {
        int bytes = payload.data().readableBytes();
        String route = RSocketTracePropagation.routeOf(payload);
        TraceContext parent = RSocketTracePropagation.extract(propagator, payload);
        Span span = tracer.nextSpan(parent)
                .name("RSOCKET " + route)
                .kind(Span.Kind.SERVER)
                .tag("protocol", "rsocket")
                .tag("rsocket.role", "responder")
                .tag("rsocket.route", route)
                .tag("messaging.message.payload_size_bytes", String.valueOf(bytes));
        metrics.onServerRequestStart();
        return new Interaction(span, contextOf(span), payload, route, bytes);
    }

    private void endClient(Interaction interaction, long startNanos, SignalType signal) {
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
        boolean error = signal == SignalType.ON_ERROR;
        tagResponseBytes(interaction);
        tracer.finishSpan(interaction.span);
        metrics.onClientCallEnd(remoteName, durationMillis, error, interaction.totalBytes());
        metrics.onDestinationResult(remoteName, !error);
    }

    private void endServer(Interaction interaction, long startNanos, SignalType signal) {
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
        boolean error = signal == SignalType.ON_ERROR;
        tagResponseBytes(interaction);
        tracer.finishSpan(interaction.span);
        metrics.onServerRequestEnd(interaction.route, durationMillis, error, interaction.totalBytes());
    }

    private static void tagResponseBytes(Interaction interaction) {
        interaction.span.tag("messaging.response.payload_size_bytes", String.valueOf(interaction.responseBytes));
    }

    private static TraceContext contextOf(Span span) {
        return new TraceContext(span.getTraceId(), span.getSpanId(), span.getParentSpanId(), span.isSampled());
    }

    private static final class Interaction {
        private final Span span;
        private final TraceContext context;
        private final Payload outbound;
        private final String route;
        private final int requestBytes;
        private long responseBytes;

        private Interaction(Span span, TraceContext context, Payload outbound, String route, int bytes) {
            this.span = span;
            this.context = context;
            this.outbound = outbound;
            this.route = route;
            this.requestBytes = bytes;
        }

        private void recordResponse(Payload payload) {
            if (payload != null) {
                responseBytes += payload.data().readableBytes();
            }
        }

        private long totalBytes() {
            return requestBytes + responseBytes;
        }
    }

    private final class TracingRSocket extends RSocketProxy {
        private TracingRSocket(RSocket source) {
            super(source);
        }

        @Override
        public Mono<Void> fireAndForget(Payload payload) {
            if (direction == Direction.REQUESTER) {
                return Mono.deferContextual(view -> {
                    Interaction interaction = startClient(payload, view);
                    long startNanos = System.nanoTime();
                    return super.fireAndForget(interaction.outbound)
                            .doOnError(interaction.span::error)
                            .doFinally(signal -> endClient(interaction, startNanos, signal));
                });
            }
            Interaction interaction = startServer(payload);
            long startNanos = System.nanoTime();
            return super.fireAndForget(payload)
                    .doOnError(interaction.span::error)
                    .doFinally(signal -> endServer(interaction, startNanos, signal))
                    .contextWrite(ctx -> ctx.put(TraceContextThreadLocalAccessor.KEY, interaction.context));
        }

        @Override
        public Mono<Payload> requestResponse(Payload payload) {
            if (direction == Direction.REQUESTER) {
                return Mono.deferContextual(view -> {
                    Interaction interaction = startClient(payload, view);
                    long startNanos = System.nanoTime();
                    return super.requestResponse(interaction.outbound)
                            .doOnNext(interaction::recordResponse)
                            .doOnError(interaction.span::error)
                            .doFinally(signal -> endClient(interaction, startNanos, signal));
                });
            }
            Interaction interaction = startServer(payload);
            long startNanos = System.nanoTime();
            return super.requestResponse(payload)
                    .doOnNext(interaction::recordResponse)
                    .doOnError(interaction.span::error)
                    .doFinally(signal -> endServer(interaction, startNanos, signal))
                    .contextWrite(ctx -> ctx.put(TraceContextThreadLocalAccessor.KEY, interaction.context));
        }

        @Override
        public Flux<Payload> requestStream(Payload payload) {
            if (direction == Direction.REQUESTER) {
                return Flux.deferContextual(view -> {
                    Interaction interaction = startClient(payload, view);
                    long startNanos = System.nanoTime();
                    return super.requestStream(interaction.outbound)
                            .doOnNext(interaction::recordResponse)
                            .doOnError(interaction.span::error)
                            .doFinally(signal -> endClient(interaction, startNanos, signal));
                });
            }
            Interaction interaction = startServer(payload);
            long startNanos = System.nanoTime();
            return super.requestStream(payload)
                    .doOnNext(interaction::recordResponse)
                    .doOnError(interaction.span::error)
                    .doFinally(signal -> endServer(interaction, startNanos, signal))
                    .contextWrite(ctx -> ctx.put(TraceContextThreadLocalAccessor.KEY, interaction.context));
        }
    }
}
