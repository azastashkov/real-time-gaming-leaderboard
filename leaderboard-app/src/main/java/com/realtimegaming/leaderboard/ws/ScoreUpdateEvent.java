package com.realtimegaming.leaderboard.ws;

import java.time.Instant;

public record ScoreUpdateEvent(String playerId, long score, Instant at, String sourceInstance) {

    public static ScoreUpdateEvent of(String playerId, long score, String sourceInstance) {
        return new ScoreUpdateEvent(playerId, score, Instant.now(), sourceInstance);
    }
}
