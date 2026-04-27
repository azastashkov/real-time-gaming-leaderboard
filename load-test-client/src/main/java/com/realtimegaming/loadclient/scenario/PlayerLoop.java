package com.realtimegaming.loadclient.scenario;

import com.realtimegaming.loadclient.ApiClient;
import com.realtimegaming.loadclient.LoadConfig;
import com.realtimegaming.loadclient.TestPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerLoop implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(PlayerLoop.class);

    private final ApiClient api;
    private final TestPlayer player;
    private final LoadConfig config;
    private final AtomicBoolean stop;

    public PlayerLoop(ApiClient api, TestPlayer player, LoadConfig config, AtomicBoolean stop) {
        this.api = api;
        this.player = player;
        this.config = config;
        this.stop = stop;
    }

    @Override
    public void run() {
        long jitter = ThreadLocalRandom.current().nextLong(config.cycleIntervalMs());
        try {
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        while (!stop.get()) {
            long score = ThreadLocalRandom.current().nextLong(1_000_001);
            try {
                api.submitScore(player, score);
            } catch (Exception ignored) {}
            try {
                api.fetch("/api/leaderboard/top", player, "top");
            } catch (Exception ignored) {}
            try {
                api.fetch("/api/leaderboard/me", player, "me");
            } catch (Exception ignored) {}
            try {
                api.fetch("/api/leaderboard/me/neighbors", player, "neighbors");
            } catch (Exception ignored) {}
            try {
                Thread.sleep(config.cycleIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
