package com.realtimegaming.leaderboard.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimegaming.leaderboard.dto.PlayerRankResponse;
import com.realtimegaming.leaderboard.leaderboard.LeaderboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LeaderboardEventSubscriberTest {

    @Mock SimpMessagingTemplate messaging;
    @Mock SimpUserRegistry userRegistry;
    @Mock LeaderboardService leaderboardService;
    @Mock SimpUser simpUser;

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    LeaderboardEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new LeaderboardEventSubscriber(objectMapper, messaging, userRegistry, leaderboardService);
    }

    @Test
    void pushesRankToLocallyConnectedUser() {
        when(userRegistry.getUser("42")).thenReturn(simpUser);
        when(leaderboardService.globalRank("42")).thenReturn(7L);

        subscriber.handle(new ScoreUpdateEvent("42", 1234L, Instant.now(), "src"));

        ArgumentCaptor<PlayerRankResponse> payload = ArgumentCaptor.forClass(PlayerRankResponse.class);
        verify(messaging).convertAndSendToUser(eq("42"), eq("/queue/rank"), payload.capture());
        assertThat(payload.getValue().playerId()).isEqualTo(42L);
        assertThat(payload.getValue().rank()).isEqualTo(7L);
        assertThat(payload.getValue().score()).isEqualTo(1234L);
    }

    @Test
    void skipsBroadcast_whenUserNotLocallyConnected() {
        when(userRegistry.getUser("42")).thenReturn(null);

        subscriber.handle(new ScoreUpdateEvent("42", 1234L, Instant.now(), "src"));

        verify(messaging, never()).convertAndSendToUser(eq("42"), eq("/queue/rank"), org.mockito.ArgumentMatchers.any());
        verify(leaderboardService, never()).globalRank(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void onMessage_deserializesPayloadAndPushes() throws Exception {
        when(userRegistry.getUser("42")).thenReturn(simpUser);
        when(leaderboardService.globalRank("42")).thenReturn(3L);

        ScoreUpdateEvent ev = new ScoreUpdateEvent("42", 99L, Instant.now(), "src");
        byte[] body = objectMapper.writeValueAsBytes(ev);
        org.springframework.data.redis.connection.Message msg =
                new org.springframework.data.redis.connection.DefaultMessage(LeaderboardEventPublisher.CHANNEL.getBytes(), body);

        subscriber.onMessage(msg, null);

        verify(messaging, times(1)).convertAndSendToUser(eq("42"), eq("/queue/rank"), org.mockito.ArgumentMatchers.any());
    }
}
