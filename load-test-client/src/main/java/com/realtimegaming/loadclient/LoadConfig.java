package com.realtimegaming.loadclient;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loadtest")
public record LoadConfig(
        String baseUrl,
        String wsUrl,
        int numPlayers,
        int rampUpSeconds,
        int durationSeconds,
        int holdAfterSeconds,
        long cycleIntervalMs,
        int wsSubscribers,
        int registerConcurrency,
        boolean exitAfterRun
) {
    public LoadConfig {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost";
        if (wsUrl == null || wsUrl.isBlank()) wsUrl = "ws://localhost/ws";
        if (numPlayers <= 0) numPlayers = 200;
        if (rampUpSeconds <= 0) rampUpSeconds = 10;
        if (durationSeconds <= 0) durationSeconds = 60;
        if (holdAfterSeconds < 0) holdAfterSeconds = 30;
        if (cycleIntervalMs <= 0) cycleIntervalMs = 1000;
        if (wsSubscribers < 0) wsSubscribers = 0;
        if (registerConcurrency <= 0) registerConcurrency = 16;
    }
}
