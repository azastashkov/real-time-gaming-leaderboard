package com.realtimegaming.leaderboard.ws;

import com.realtimegaming.leaderboard.dto.LeaderboardEntry;
import com.realtimegaming.leaderboard.dto.LeaderboardResponse;
import com.realtimegaming.leaderboard.leaderboard.LeaderboardService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ScheduledBroadcaster {

    public static final String TOP10_TOPIC = "/topic/leaderboard/top10";
    private static final Logger log = LoggerFactory.getLogger(ScheduledBroadcaster.class);

    private final LeaderboardService service;
    private final SimpMessagingTemplate messaging;
    private final Counter broadcasts;

    public ScheduledBroadcaster(LeaderboardService service,
                                SimpMessagingTemplate messaging,
                                MeterRegistry meterRegistry) {
        this.service = service;
        this.messaging = messaging;
        this.broadcasts = Counter.builder("leaderboard.broadcasts").register(meterRegistry);
    }

    @Scheduled(fixedRateString = "${leaderboard.broadcast-interval-ms:1000}")
    public void broadcastTop10() {
        try {
            List<LeaderboardEntry> entries = service.topN(10);
            messaging.convertAndSend(TOP10_TOPIC, new LeaderboardResponse(entries, Instant.now()));
            broadcasts.increment();
        } catch (Exception e) {
            log.warn("top10 broadcast failed", e);
        }
    }
}
