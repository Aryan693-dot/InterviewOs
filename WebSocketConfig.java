package com.interviewos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Replaces Socket.io in server.js.
 *
 * STOMP over WebSocket with SockJS fallback.
 * Frontend usage:
 *   const client = new Client({ brokerURL: 'ws://localhost:5000/ws' });
 *   client.subscribe('/topic/room/ROOM_ID', handler);
 *   client.publish({ destination: '/app/room/ROOM_ID/code', body: JSON.stringify({code}) });
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .withSockJS();     // SockJS fallback for browsers that don't support WS
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Messages from client go to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
        // Messages broadcast to subscribers
        registry.enableSimpleBroker("/topic", "/queue");
        // For private user messages
        registry.setUserDestinationPrefix("/user");
    }
}
