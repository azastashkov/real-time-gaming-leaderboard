package com.realtimegaming.leaderboard.leaderboard;

import com.realtimegaming.leaderboard.dto.LeaderboardEntry;
import com.realtimegaming.leaderboard.player.PlayerService;
import com.realtimegaming.leaderboard.ws.LeaderboardEventPublisher;
import com.realtimegaming.leaderboard.ws.ScoreUpdateEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaderboardServiceTest {

    private static final int PARTITIONS = 4;

    @Mock LeaderboardRedisRepository repo;
    @Mock LeaderboardEventPublisher publisher;
    @Mock PlayerService playerService;

    LeaderboardService service;
    PartitionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PartitionStrategy(PARTITIONS);
        service = new LeaderboardService(repo, strategy, publisher, playerService,
                new SimpleMeterRegistry(), "test-instance");
    }

    @Test
    void submitScore_writesToRedisAndPublishesEvent() {
        service.submitScore(42L, 1000L);

        verify(repo).addScore("42", 1000L);
        verify(playerService).recordScore(42L, 1000L);
        ArgumentCaptor<ScoreUpdateEvent> captor = ArgumentCaptor.forClass(ScoreUpdateEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().playerId()).isEqualTo("42");
        assertThat(captor.getValue().score()).isEqualTo(1000L);
        assertThat(captor.getValue().sourceInstance()).isEqualTo("test-instance");
    }

    @Test
    void topN_mergesAcrossPartitions_byScoreDescThenIdAsc() {
        when(repo.topNFromPartition(0, 10)).thenReturn(List.of(
                new ScoredEntry("p0a", 100L), new ScoredEntry("p0b", 80L)));
        when(repo.topNFromPartition(1, 10)).thenReturn(List.of(
                new ScoredEntry("p1a", 95L), new ScoredEntry("p1b", 70L)));
        when(repo.topNFromPartition(2, 10)).thenReturn(List.of(
                new ScoredEntry("p2a", 90L), new ScoredEntry("p2b", 60L)));
        when(repo.topNFromPartition(3, 10)).thenReturn(List.of(
                new ScoredEntry("p3a", 85L), new ScoredEntry("p3b", 50L)));

        List<LeaderboardEntry> top = service.topN(10);

        assertThat(top).hasSize(8);
        assertThat(top.stream().map(LeaderboardEntry::playerId).toList())
                .containsExactly("p0a", "p1a", "p2a", "p3a", "p0b", "p1b", "p2b", "p3b");
        assertThat(top.stream().map(LeaderboardEntry::rank).toList())
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    @Test
    void topN_breaksTiesByPlayerIdAscending() {
        when(repo.topNFromPartition(0, 10)).thenReturn(List.of(new ScoredEntry("zeta", 100L)));
        when(repo.topNFromPartition(1, 10)).thenReturn(List.of(new ScoredEntry("alpha", 100L)));
        when(repo.topNFromPartition(2, 10)).thenReturn(List.of(new ScoredEntry("mike", 100L)));
        when(repo.topNFromPartition(3, 10)).thenReturn(List.of());

        List<LeaderboardEntry> top = service.topN(10);

        assertThat(top.stream().map(LeaderboardEntry::playerId).toList())
                .containsExactly("alpha", "mike", "zeta");
    }

    @Test
    void topN_truncatesToN() {
        for (int p = 0; p < PARTITIONS; p++) {
            when(repo.topNFromPartition(p, 3)).thenReturn(List.of(
                    new ScoredEntry("p" + p + "a", 100L - p),
                    new ScoredEntry("p" + p + "b", 90L - p),
                    new ScoredEntry("p" + p + "c", 80L - p)));
        }
        List<LeaderboardEntry> top = service.topN(3);
        assertThat(top).hasSize(3);
    }

    @Test
    void globalRank_unknownPlayer_returnsNull() {
        when(repo.scoreOf("ghost")).thenReturn(null);
        assertThat(service.globalRank("ghost")).isNull();
    }

    @Test
    void globalRank_sumsLocalRankAndStrictlyAboveAcrossPartitions() {
        String userId = "user-X";
        long score = 500L;
        int userPart = strategy.partitionFor(userId);

        when(repo.scoreOf(userId)).thenReturn(score);
        when(repo.reverseRankInPartition(userPart, userId)).thenReturn(2L);
        for (int p = 0; p < PARTITIONS; p++) {
            if (p == userPart) continue;
            when(repo.countStrictlyAbove(p, score)).thenReturn(3L);
            when(repo.exactScoreMembers(p, score)).thenReturn(Set.of());
        }

        Long rank = service.globalRank(userId);

        assertThat(rank).isEqualTo(2L + 3L * (PARTITIONS - 1) + 1L);
    }

    @Test
    void globalRank_includesCrossPartitionTiesByLexLess() {
        String userId = "mike";
        long score = 100L;
        int userPart = strategy.partitionFor(userId);

        when(repo.scoreOf(userId)).thenReturn(score);
        when(repo.reverseRankInPartition(userPart, userId)).thenReturn(0L);

        boolean tieAdded = false;
        for (int p = 0; p < PARTITIONS; p++) {
            if (p == userPart) continue;
            when(repo.countStrictlyAbove(p, score)).thenReturn(0L);
            if (!tieAdded) {
                when(repo.exactScoreMembers(p, score)).thenReturn(Set.of("alpha", "zach"));
                tieAdded = true;
            } else {
                when(repo.exactScoreMembers(p, score)).thenReturn(Set.of());
            }
        }

        Long rank = service.globalRank(userId);

        assertThat(rank).isEqualTo(2L);
    }

    @Test
    void neighbors_returns4AboveAnd4BelowAroundUser() {
        String userId = "mid";
        long score = 50L;
        int userPart = strategy.partitionFor(userId);

        when(repo.scoreOf(userId)).thenReturn(score);
        when(repo.reverseRankInPartition(userPart, userId)).thenReturn(5L);
        for (int p = 0; p < PARTITIONS; p++) {
            if (p == userPart) {
                when(repo.ascendingAboveScore(p, score, 9)).thenReturn(List.of(
                        new ScoredEntry("a-p" + p + "-1", 51L)));
                when(repo.descendingAtOrBelowScore(p, score, 10)).thenReturn(List.of(
                        new ScoredEntry(userId, 50L),
                        new ScoredEntry("b-p" + p + "-1", 49L)));
            } else {
                when(repo.ascendingAboveScore(p, score, 9)).thenReturn(List.of(
                        new ScoredEntry("a-p" + p + "-1", 52L),
                        new ScoredEntry("a-p" + p + "-2", 53L)));
                when(repo.descendingAtOrBelowScore(p, score, 10)).thenReturn(List.of(
                        new ScoredEntry("b-p" + p + "-1", 48L),
                        new ScoredEntry("b-p" + p + "-2", 47L)));
                when(repo.countStrictlyAbove(p, score)).thenReturn(2L);
                when(repo.exactScoreMembers(p, score)).thenReturn(Set.of());
            }
        }

        LeaderboardService.NeighborsResult result = service.neighbors(userId, 4);

        assertThat(result).isNotNull();
        assertThat(result.playerId()).isEqualTo(userId);
        assertThat(result.score()).isEqualTo(50L);
        assertThat(result.neighbors()).isNotEmpty();
        boolean foundUser = result.neighbors().stream().anyMatch(n -> n.playerId().equals(userId));
        assertThat(foundUser).isTrue();
        long previousScore = Long.MAX_VALUE;
        for (LeaderboardEntry e : result.neighbors()) {
            assertThat(e.score()).isLessThanOrEqualTo(previousScore);
            previousScore = e.score();
        }
    }

    @Test
    void neighbors_unknownPlayer_returnsNull() {
        when(repo.scoreOf("ghost")).thenReturn(null);
        assertThat(service.neighbors("ghost", 4)).isNull();
        verify(repo, never()).ascendingAboveScore(anyInt(), anyLong(), anyLong());
    }
}
