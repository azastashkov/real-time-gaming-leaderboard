package com.realtimegaming.leaderboard.leaderboard;

public record ScoredEntry(String playerId, long score) {

    public static final java.util.Comparator<ScoredEntry> GLOBAL_ORDER =
            java.util.Comparator.<ScoredEntry>comparingLong(e -> -e.score()).thenComparing(ScoredEntry::playerId);
}
