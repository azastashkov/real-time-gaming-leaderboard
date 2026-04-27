package com.realtimegaming.leaderboard.player;

import com.realtimegaming.leaderboard.dto.PlayerProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PlayerService {

    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Player findById(long id) {
        return repository.findById(id).orElseThrow(() -> new PlayerNotFoundException("playerId=" + id));
    }

    @Transactional(readOnly = true)
    public PlayerProfileResponse profile(long id) {
        Player p = findById(id);
        return new PlayerProfileResponse(p.getId(), p.getUsername(), p.getLastScore(), p.getLastPlayedAt(), p.getCreatedAt());
    }

    @Transactional
    public void recordScore(long playerId, long score) {
        repository.findById(playerId).ifPresent(p -> {
            p.setLastScore(score);
            p.setLastPlayedAt(Instant.now());
        });
    }
}
