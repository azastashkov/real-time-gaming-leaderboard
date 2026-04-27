package com.realtimegaming.leaderboard.leaderboard;

import com.realtimegaming.leaderboard.dto.ScoreSubmitRequest;
import com.realtimegaming.leaderboard.security.AuthenticatedPlayer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final LeaderboardService service;

    public ScoreController(LeaderboardService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@Valid @RequestBody ScoreSubmitRequest request, AuthenticatedPlayer player) {
        service.submitScore(player.id(), request.score());
    }
}
