package com.realtimegaming.leaderboard.leaderboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PartitionStrategy {

    private final int numPartitions;

    public PartitionStrategy(@Value("${leaderboard.partitions:4}") int numPartitions) {
        if (numPartitions < 1) {
            throw new IllegalArgumentException("leaderboard.partitions must be >= 1");
        }
        this.numPartitions = numPartitions;
    }

    public int numPartitions() {
        return numPartitions;
    }

    public int partitionFor(String playerId) {
        return Math.floorMod(playerId.hashCode(), numPartitions);
    }

    public String keyFor(int partition) {
        if (partition < 0 || partition >= numPartitions) {
            throw new IllegalArgumentException("partition out of range: " + partition);
        }
        return "{lb:p:" + partition + "}:scores";
    }

    public String keyFor(String playerId) {
        return keyFor(partitionFor(playerId));
    }
}
