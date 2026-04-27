package com.realtimegaming.leaderboard.dto;

public record PlayerRankResponse(long playerId, String username, long score, Long rank) {}
