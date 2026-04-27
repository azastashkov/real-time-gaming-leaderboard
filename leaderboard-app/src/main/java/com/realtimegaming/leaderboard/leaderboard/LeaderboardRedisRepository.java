package com.realtimegaming.leaderboard.leaderboard;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class LeaderboardRedisRepository {

    private final StringRedisTemplate redis;
    private final PartitionStrategy strategy;

    public LeaderboardRedisRepository(StringRedisTemplate redis, PartitionStrategy strategy) {
        this.redis = redis;
        this.strategy = strategy;
    }

    public void addScore(String playerId, long score) {
        redis.opsForZSet().add(strategy.keyFor(playerId), playerId, score);
    }

    public Long scoreOf(String playerId) {
        Double s = redis.opsForZSet().score(strategy.keyFor(playerId), playerId);
        return s == null ? null : s.longValue();
    }

    public List<ScoredEntry> topNFromPartition(int partition, int n) {
        Set<ZSetOperations.TypedTuple<String>> result =
                redis.opsForZSet().reverseRangeWithScores(strategy.keyFor(partition), 0, n - 1);
        return toEntries(result);
    }

    public Long reverseRankInPartition(int partition, String playerId) {
        return redis.opsForZSet().reverseRank(strategy.keyFor(partition), playerId);
    }

    public Long countStrictlyAbove(int partition, long score) {
        return redis.opsForZSet().count(
                strategy.keyFor(partition),
                Math.nextUp((double) score),
                Double.POSITIVE_INFINITY);
    }

    public List<ScoredEntry> ascendingAboveScore(int partition, long score, long limit) {
        Set<ZSetOperations.TypedTuple<String>> result = redis.opsForZSet()
                .rangeByScoreWithScores(
                        strategy.keyFor(partition),
                        Math.nextUp((double) score),
                        Double.POSITIVE_INFINITY,
                        0, limit);
        return toEntries(result);
    }

    public List<ScoredEntry> descendingAtOrBelowScore(int partition, long score, long limit) {
        Set<ZSetOperations.TypedTuple<String>> result = redis.opsForZSet()
                .reverseRangeByScoreWithScores(
                        strategy.keyFor(partition),
                        Double.NEGATIVE_INFINITY,
                        score,
                        0, limit);
        return toEntries(result);
    }

    public Set<String> exactScoreMembers(int partition, long score) {
        Set<String> members = redis.opsForZSet().rangeByScore(strategy.keyFor(partition), score, score);
        return members == null ? Set.of() : members;
    }

    public long sizeOfPartition(int partition) {
        Long size = redis.opsForZSet().size(strategy.keyFor(partition));
        return size == null ? 0 : size;
    }

    private static List<ScoredEntry> toEntries(Set<ZSetOperations.TypedTuple<String>> set) {
        if (set == null || set.isEmpty()) return List.of();
        return set.stream()
                .map(t -> new ScoredEntry(
                        t.getValue(),
                        t.getScore() == null ? 0L : t.getScore().longValue()))
                .toList();
    }
}
