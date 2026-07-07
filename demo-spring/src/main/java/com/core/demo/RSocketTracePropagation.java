package com.core.demo;

import com.core.tracing.propagation.Propagator;
import com.core.tracing.propagation.TraceContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.metadata.RoutingMetadata;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.util.DefaultPayload;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public final class RSocketTracePropagation {
    public static final String TRACEPARENT_MIME = "messaging/x.mini.traceparent";
    private static final String ROUTING_MIME = WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString();

    private static final Propagator.Setter<CompositeByteBuf> SETTER = (carrier, key, value) -> {
        ByteBuf content = ByteBufAllocator.DEFAULT.buffer();
        content.writeCharSequence(value, StandardCharsets.UTF_8);
        CompositeMetadataCodec.encodeAndAddMetadata(carrier, ByteBufAllocator.DEFAULT, TRACEPARENT_MIME, content);
    };

    private static final Propagator.Getter<Payload> GETTER =
            (carrier, key) -> readEntry(carrier, TRACEPARENT_MIME);

    private RSocketTracePropagation() {
    }

    public static TraceContext extract(Propagator propagator, Payload payload) {
        return propagator.extract(payload, GETTER);
    }

    public static Payload inject(Propagator propagator, TraceContext context, Payload payload) {
        CompositeByteBuf metadata = ByteBufAllocator.DEFAULT.compositeBuffer();
        try {
            if (payload.hasMetadata()) {
                metadata.addComponent(true, Unpooled.wrappedBuffer(payload.getMetadata()));
            }
            propagator.inject(context, metadata, SETTER);
            ByteBuffer data = ByteBuffer.wrap(ByteBufUtil.getBytes(payload.data()));
            ByteBuffer merged = ByteBuffer.wrap(ByteBufUtil.getBytes(metadata));
            return DefaultPayload.create(data, merged);
        } finally {
            metadata.release();
        }
    }

    public static String routeOf(Payload payload) {
        if (!payload.hasMetadata()) {
            return "unknown";
        }
        CompositeMetadata metadata = new CompositeMetadata(payload.sliceMetadata(), false);
        for (CompositeMetadata.Entry entry : metadata) {
            if (ROUTING_MIME.equals(entry.getMimeType())) {
                Iterator<String> tags = new RoutingMetadata(entry.getContent()).iterator();
                if (tags.hasNext()) {
                    return tags.next();
                }
            }
        }
        return "unknown";
    }

    private static String readEntry(Payload payload, String mime) {
        if (!payload.hasMetadata()) {
            return null;
        }
        CompositeMetadata metadata = new CompositeMetadata(payload.sliceMetadata(), false);
        for (CompositeMetadata.Entry entry : metadata) {
            if (mime.equals(entry.getMimeType())) {
                return entry.getContent().toString(StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
