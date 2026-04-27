package com.realtimegaming.leaderboard.leaderboard;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartitionStrategyTest {

    @Test
    void partitionFor_isDeterministicAndInRange() {
        PartitionStrategy strategy = new PartitionStrategy(4);
        for (int i = 0; i < 1000; i++) {
            int p = strategy.partitionFor("player-" + i);
            assertThat(p).isBetween(0, 3);
            assertThat(p).isEqualTo(strategy.partitionFor("player-" + i));
        }
    }

    @Test
    void partitionFor_distributesAcrossAllPartitions() {
        PartitionStrategy strategy = new PartitionStrategy(4);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(strategy.partitionFor("player-" + i));
        }
        assertThat(seen).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void keyFor_usesHashTagFormat() {
        PartitionStrategy strategy = new PartitionStrategy(4);
        for (int p = 0; p < 4; p++) {
            assertThat(strategy.keyFor(p)).isEqualTo("{lb:p:" + p + "}:scores");
        }
    }

    @Test
    void keyFor_byPlayerId_matchesPartition() {
        PartitionStrategy strategy = new PartitionStrategy(4);
        String pid = "alice-42";
        int p = strategy.partitionFor(pid);
        assertThat(strategy.keyFor(pid)).isEqualTo("{lb:p:" + p + "}:scores");
    }

    @Test
    void invalidPartitionCount_isRejected() {
        assertThatThrownBy(() -> new PartitionStrategy(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartitionStrategy(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keyFor_outOfRange_isRejected() {
        PartitionStrategy strategy = new PartitionStrategy(4);
        assertThatThrownBy(() -> strategy.keyFor(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> strategy.keyFor(4)).isInstanceOf(IllegalArgumentException.class);
    }
}
