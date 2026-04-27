package com.realtimegaming.leaderboard.player;

import com.realtimegaming.leaderboard.dto.PlayerProfileResponse;
import com.realtimegaming.leaderboard.security.AuthenticatedPlayer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping("/me")
    public PlayerProfileResponse me(AuthenticatedPlayer player) {
        return playerService.profile(player.id());
    }
}
