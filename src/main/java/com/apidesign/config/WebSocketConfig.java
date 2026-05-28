package com.apidesign.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket bridge. Used by {@link com.apidesign.event.listener.WebSocketOrderBridge}
 * to push order events to authenticated users.
 *
 * <p>The default in-memory simple broker is fine for local dev. For production-grade
 * horizontal scaling, swap to an external broker (RabbitMQ relay) — leaving that out of
 * scope deliberately. Likewise, JWT-on-handshake (CONNECT frame) auth is non-trivial and
 * intentionally not wired here.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS fallback for browsers/proxies that don't support raw WebSocket.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // /topic for broadcast (one publisher → many subscribers).
        config.enableSimpleBroker("/topic");
        // /app is the prefix clients use when SENDing to controller-handled destinations.
        config.setApplicationDestinationPrefixes("/app");
    }
}
