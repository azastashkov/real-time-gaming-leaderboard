package com.realtimegaming.leaderboard.leaderboard;

import com.realtimegaming.leaderboard.dto.LeaderboardEntry;
import com.realtimegaming.leaderboard.dto.LeaderboardResponse;
import com.realtimegaming.leaderboard.dto.NeighborsResponse;
import com.realtimegaming.leaderboard.dto.PlayerRankResponse;
import com.realtimegaming.leaderboard.security.AuthenticatedPlayer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private static final int TOP_SIZE = 10;
    private static final int NEIGHBORS_RADIUS = 4;

    private final LeaderboardService service;

    public LeaderboardController(LeaderboardService service) {
        this.service = service;
    }

    @GetMapping("/top")
    public LeaderboardResponse top() {
        return new LeaderboardResponse(service.topN(TOP_SIZE), Instant.now());
    }

    @GetMapping("/me")
    public PlayerRankResponse me(AuthenticatedPlayer player) {
        String pid = String.valueOf(player.id());
        Long score = service.scoreOf(pid);
        Long rank = service.globalRank(pid);
        return new PlayerRankResponse(player.id(), player.username(), score == null ? 0L : score, rank);
    }

    @GetMapping("/me/neighbors")
    public NeighborsResponse neighbors(AuthenticatedPlayer player) {
        String pid = String.valueOf(player.id());
        LeaderboardService.NeighborsResult result = service.neighbors(pid, NEIGHBORS_RADIUS);
        if (result == null) {
            return new NeighborsResponse(pid, 0L, 0L, List.of());
        }
        return new NeighborsResponse(pid, result.score(), result.rank(), result.neighbors());
    }
}
