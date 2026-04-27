package com.realtimegaming.leaderboard.dto;

import java.time.Instant;

public record PlayerProfileResponse(long id, String username, long lastScore, Instant lastPlayedAt, Instant createdAt) {}
