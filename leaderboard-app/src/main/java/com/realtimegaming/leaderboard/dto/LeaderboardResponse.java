package com.realtimegaming.leaderboard.dto;

import java.time.Instant;
import java.util.List;

public record LeaderboardResponse(List<LeaderboardEntry> entries, Instant generatedAt) {}
