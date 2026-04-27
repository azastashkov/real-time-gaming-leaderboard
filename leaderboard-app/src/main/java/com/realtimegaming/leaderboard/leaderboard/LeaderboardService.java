package com.realtimegaming.leaderboard.leaderboard;

import com.realtimegaming.leaderboard.dto.LeaderboardEntry;
import com.realtimegaming.leaderboard.player.PlayerService;
import com.realtimegaming.leaderboard.ws.LeaderboardEventPublisher;
import com.realtimegaming.leaderboard.ws.ScoreUpdateEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Service
public class LeaderboardService {

    private final LeaderboardRedisRepository repo;
    private final PartitionStrategy strategy;
    private final LeaderboardEventPublisher publisher;
    private final PlayerService playerService;
    private final String instanceId;

    private final Counter scoreCounter;
    private final Timer topTimer;
    private final Timer rankTimer;
    private final Timer neighborsTimer;

    public LeaderboardService(LeaderboardRedisRepository repo,
                              PartitionStrategy strategy,
                              LeaderboardEventPublisher publisher,
                              PlayerService playerService,
                              MeterRegistry meterRegistry,
                              @Value("${app.instance:unknown}") String instanceId) {
        this.repo = repo;
        this.strategy = strategy;
        this.publisher = publisher;
        this.playerService = playerService;
        this.instanceId = instanceId;
        this.scoreCounter = Counter.builder("leaderboard.scores.submitted").register(meterRegistry);
        this.topTimer = Timer.builder("leaderboard.top.duration").register(meterRegistry);
        this.rankTimer = Timer.builder("leaderboard.rank.duration").register(meterRegistry);
        this.neighborsTimer = Timer.builder("leaderboard.neighbors.duration").register(meterRegistry);
    }

    public void submitScore(long playerId, long score) {
        String pid = String.valueOf(playerId);
        repo.addScore(pid, score);
        playerService.recordScore(playerId, score);
        publisher.publish(ScoreUpdateEvent.of(pid, score, instanceId));
        scoreCounter.increment();
    }

    public Long scoreOf(String playerId) {
        return repo.scoreOf(playerId);
    }

    public List<LeaderboardEntry> topN(int n) {
        return topTimer.record(() -> {
            List<ScoredEntry> all = new ArrayList<>(strategy.numPartitions() * n);
            for (int p = 0; p < strategy.numPartitions(); p++) {
                all.addAll(repo.topNFromPartition(p, n));
            }
            all.sort(ScoredEntry.GLOBAL_ORDER);
            int limit = Math.min(n, all.size());
            List<LeaderboardEntry> result = new ArrayList<>(limit);
            for (int i = 0; i < limit; i++) {
                ScoredEntry e = all.get(i);
                result.add(new LeaderboardEntry(i + 1L, e.playerId(), e.score()));
            }
            return result;
        });
    }

    public Long globalRank(String playerId) {
        return rankTimer.record(() -> {
            Long score = repo.scoreOf(playerId);
            if (score == null) return null;
            int userPart = strategy.partitionFor(playerId);
            long ahead = 0;
            for (int p = 0; p < strategy.numPartitions(); p++) {
                if (p == userPart) {
                    Long localRank = repo.reverseRankInPartition(p, playerId);
                    if (localRank != null) ahead += localRank;
                } else {
                    Long strictlyAbove = repo.countStrictlyAbove(p, score);
                    if (strictlyAbove != null) ahead += strictlyAbove;
                    Set<String> tied = repo.exactScoreMembers(p, score);
                    long tiedAbove = tied.stream().filter(m -> m.compareTo(playerId) < 0).count();
                    ahead += tiedAbove;
                }
            }
            return ahead + 1;
        });
    }

    public NeighborsResult neighbors(String playerId, int radius) {
        return neighborsTimer.record(() -> {
            Long score = repo.scoreOf(playerId);
            if (score == null) return null;
            Long rank = globalRank(playerId);
            if (rank == null) return null;

            int windowSize = radius + 5;
            List<ScoredEntry> candidates = new ArrayList<>();
            for (int p = 0; p < strategy.numPartitions(); p++) {
                candidates.addAll(repo.ascendingAboveScore(p, score, windowSize));
                candidates.addAll(repo.descendingAtOrBelowScore(p, score, windowSize + 1));
            }

            LinkedHashMap<String, ScoredEntry> dedup = new LinkedHashMap<>();
            for (ScoredEntry e : candidates) dedup.putIfAbsent(e.playerId(), e);

            List<ScoredEntry> sorted = new ArrayList<>(dedup.values());
            sorted.sort(ScoredEntry.GLOBAL_ORDER);

            int userIdx = -1;
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).playerId().equals(playerId)) {
                    userIdx = i;
                    break;
                }
            }
            if (userIdx == -1) return null;

            int from = Math.max(0, userIdx - radius);
            int to = Math.min(sorted.size(), userIdx + radius + 1);
            long startRank = rank - (userIdx - from);

            List<LeaderboardEntry> entries = new ArrayList<>(to - from);
            long r = startRank;
            for (int i = from; i < to; i++) {
                ScoredEntry e = sorted.get(i);
                entries.add(new LeaderboardEntry(r++, e.playerId(), e.score()));
            }
            return new NeighborsResult(playerId, score, rank, entries);
        });
    }

    public record NeighborsResult(String playerId, long score, long rank, List<LeaderboardEntry> neighbors) {}
}
