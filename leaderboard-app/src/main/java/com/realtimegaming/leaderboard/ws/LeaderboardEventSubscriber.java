package com.realtimegaming.leaderboard.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimegaming.leaderboard.dto.PlayerRankResponse;
import com.realtimegaming.leaderboard.leaderboard.LeaderboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Component
public class LeaderboardEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardEventSubscriber.class);

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messaging;
    private final SimpUserRegistry userRegistry;
    private final LeaderboardService leaderboardService;

    public LeaderboardEventSubscriber(ObjectMapper objectMapper,
                                      SimpMessagingTemplate messaging,
                                      SimpUserRegistry userRegistry,
                                      LeaderboardService leaderboardService) {
        this.objectMapper = objectMapper;
        this.messaging = messaging;
        this.userRegistry = userRegistry;
        this.leaderboardService = leaderboardService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ScoreUpdateEvent event = objectMapper.readValue(message.getBody(), ScoreUpdateEvent.class);
            handle(event);
        } catch (Exception e) {
            log.warn("failed to handle score update event", e);
        }
    }

    void handle(ScoreUpdateEvent event) {
        if (userRegistry.getUser(event.playerId()) == null) {
            return;
        }
        Long rank = leaderboardService.globalRank(event.playerId());
        if (rank == null) return;
        PlayerRankResponse payload = new PlayerRankResponse(
                Long.parseLong(event.playerId()), null, event.score(), rank);
        messaging.convertAndSendToUser(event.playerId(), "/queue/rank", payload);
    }
}
