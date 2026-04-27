package com.realtimegaming.leaderboard.player;

import com.realtimegaming.leaderboard.dto.PlayerProfileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock PlayerRepository repository;
    @InjectMocks PlayerService service;

    @Test
    void findById_returnsPlayer() {
        Player p = new Player("alice", "hash");
        ReflectionTestUtils.setField(p, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        Player found = service.findById(1L);
        assertThat(found.getUsername()).isEqualTo("alice");
    }

    @Test
    void findById_throwsWhenAbsent() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void profile_mapsFields() {
        Player p = new Player("alice", "hash");
        ReflectionTestUtils.setField(p, "id", 1L);
        ReflectionTestUtils.setField(p, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        p.setLastScore(123L);
        p.setLastPlayedAt(Instant.parse("2026-04-01T00:00:00Z"));
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        PlayerProfileResponse out = service.profile(1L);

        assertThat(out.id()).isEqualTo(1L);
        assertThat(out.username()).isEqualTo("alice");
        assertThat(out.lastScore()).isEqualTo(123L);
        assertThat(out.lastPlayedAt()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void recordScore_updatesPlayerWhenPresent() {
        Player p = new Player("alice", "hash");
        ReflectionTestUtils.setField(p, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        service.recordScore(1L, 999L);

        assertThat(p.getLastScore()).isEqualTo(999L);
        assertThat(p.getLastPlayedAt()).isNotNull();
    }
}
