package com.realtimegaming.leaderboard.ws;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public StompAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = readBearer(accessor.getNativeHeader("Authorization"));
            if (token == null) {
                throw new IllegalArgumentException("missing or invalid Authorization header on STOMP CONNECT");
            }
            Jwt jwt = jwtDecoder.decode(token);
            AbstractAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
            accessor.setUser(auth);
        }
        return message;
    }

    private static String readBearer(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        String v = values.get(0);
        if (!StringUtils.hasText(v)) return null;
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        return v.trim();
    }
}
