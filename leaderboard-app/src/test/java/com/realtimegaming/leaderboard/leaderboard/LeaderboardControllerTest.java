package com.realtimegaming.leaderboard.leaderboard;

import com.realtimegaming.leaderboard.dto.LeaderboardEntry;
import com.realtimegaming.leaderboard.security.AuthenticatedPlayerArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LeaderboardControllerTest {

    @Mock LeaderboardService service;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LeaderboardController controller = new LeaderboardController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticatedPlayerArgumentResolver())
                .build();
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "HS256")
                .subject("42")
                .claim("username", "alice")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(), "42"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void top_returnsTopEntries() throws Exception {
        when(service.topN(10)).thenReturn(List.of(
                new LeaderboardEntry(1L, "1", 100L),
                new LeaderboardEntry(2L, "2", 90L)));

        mockMvc.perform(get("/api/leaderboard/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].playerId").value("1"))
                .andExpect(jsonPath("$.entries[0].score").value(100));
    }

    @Test
    void me_returnsAuthenticatedPlayerRank() throws Exception {
        when(service.scoreOf("42")).thenReturn(500L);
        when(service.globalRank("42")).thenReturn(7L);

        mockMvc.perform(get("/api/leaderboard/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(42))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.score").value(500))
                .andExpect(jsonPath("$.rank").value(7));
    }

    @Test
    void me_unscoredPlayer_returnsZeroScore() throws Exception {
        when(service.scoreOf("42")).thenReturn(null);
        when(service.globalRank("42")).thenReturn(null);

        mockMvc.perform(get("/api/leaderboard/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void neighbors_returnsAroundUser() throws Exception {
        LeaderboardService.NeighborsResult result = new LeaderboardService.NeighborsResult(
                "42", 200L, 5L, List.of(
                        new LeaderboardEntry(1L, "alpha", 300L),
                        new LeaderboardEntry(5L, "42", 200L),
                        new LeaderboardEntry(9L, "zeta", 100L)));
        when(service.neighbors("42", 4)).thenReturn(result);

        mockMvc.perform(get("/api/leaderboard/me/neighbors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value("42"))
                .andExpect(jsonPath("$.score").value(200))
                .andExpect(jsonPath("$.rank").value(5))
                .andExpect(jsonPath("$.neighbors.length()").value(3));
    }

    @Test
    void neighbors_unknownPlayer_returnsEmpty() throws Exception {
        when(service.neighbors("42", 4)).thenReturn(null);

        mockMvc.perform(get("/api/leaderboard/me/neighbors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neighbors.length()").value(0))
                .andExpect(jsonPath("$.score").value(0));
    }
}
