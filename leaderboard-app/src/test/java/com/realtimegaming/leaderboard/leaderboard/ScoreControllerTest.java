package com.realtimegaming.leaderboard.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimegaming.leaderboard.dto.ScoreSubmitRequest;
import com.realtimegaming.leaderboard.security.AuthenticatedPlayerArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScoreControllerTest {

    @Mock LeaderboardService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ScoreController controller = new ScoreController(service);
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
    void submit_invokesServiceWithAuthenticatedPlayerId() throws Exception {
        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ScoreSubmitRequest(1234L))))
                .andExpect(status().isNoContent());

        verify(service).submitScore(eq(42L), eq(1234L));
    }

    @Test
    void submit_rejectsNegativeScore() throws Exception {
        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\": -1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_rejectsMissingScore() throws Exception {
        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
