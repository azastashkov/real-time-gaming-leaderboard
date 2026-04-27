package com.realtimegaming.leaderboard.dto;

import java.time.Instant;

public record LoginResponse(String token, String tokenType, Instant expiresAt, long playerId, String username) {

    public static LoginResponse bearer(String token, Instant expiresAt, long playerId, String username) {
        return new LoginResponse(token, "Bearer", expiresAt, playerId, username);
    }
}
