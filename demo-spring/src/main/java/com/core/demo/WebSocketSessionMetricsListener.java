package com.core.demo;

import com.core.metrics.MetricsRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketSessionMetricsListener {
    private final MetricsRegistry metrics;
    private final String endpoint;
    private final Set<String> connectedSessionIds = ConcurrentHashMap.newKeySet();

    public WebSocketSessionMetricsListener(MetricsRegistry metrics, String endpoint) {
        this.metrics = metrics;
        this.endpoint = endpoint;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (sessionId != null && connectedSessionIds.add(sessionId)) {
            metrics.onConnectionOpened(endpoint);
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null && connectedSessionIds.remove(sessionId)) {
            metrics.onConnectionClosed(endpoint);
        }
    }
}
