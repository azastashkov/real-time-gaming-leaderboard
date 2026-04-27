package com.realtimegaming.leaderboard.dto;

import java.util.List;

public record NeighborsResponse(String playerId, long score, long rank, List<LeaderboardEntry> neighbors) {}
