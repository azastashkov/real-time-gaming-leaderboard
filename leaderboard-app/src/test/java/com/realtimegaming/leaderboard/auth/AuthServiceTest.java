package com.realtimegaming.leaderboard.auth;

import com.realtimegaming.leaderboard.dto.LoginRequest;
import com.realtimegaming.leaderboard.dto.LoginResponse;
import com.realtimegaming.leaderboard.dto.RegisterRequest;
import com.realtimegaming.leaderboard.player.Player;
import com.realtimegaming.leaderboard.player.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock PlayerRepository repository;
    @Mock JwtTokenService jwt;

    PasswordEncoder encoder;
    AuthService authService;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        authService = new AuthService(repository, encoder, jwt);
        when(jwt.generate(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("token-xyz");
        when(jwt.expiresAt()).thenReturn(Instant.now().plusSeconds(3600));
    }

    @Test
    void register_savesPlayerAndReturnsToken() {
        when(repository.existsByUsername("alice")).thenReturn(false);
        when(repository.save(any(Player.class))).thenAnswer(inv -> {
            Player p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 7L);
            return p;
        });

        LoginResponse resp = authService.register(new RegisterRequest("alice", "secret"));

        assertThat(resp.token()).isEqualTo("token-xyz");
        assertThat(resp.tokenType()).isEqualTo("Bearer");
        assertThat(resp.playerId()).isEqualTo(7L);
        assertThat(resp.username()).isEqualTo("alice");
        verify(repository).save(any(Player.class));
    }

    @Test
    void register_rejectsDuplicateUsername() {
        when(repository.existsByUsername("alice")).thenReturn(true);
        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "secret")))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void login_succeedsWithCorrectPassword() {
        Player p = new Player("alice", encoder.encode("secret"));
        ReflectionTestUtils.setField(p, "id", 7L);
        when(repository.findByUsername("alice")).thenReturn(Optional.of(p));

        LoginResponse resp = authService.login(new LoginRequest("alice", "secret"));

        assertThat(resp.playerId()).isEqualTo(7L);
        assertThat(resp.token()).isEqualTo("token-xyz");
    }

    @Test
    void login_failsWithBadPassword() {
        Player p = new Player("alice", encoder.encode("secret"));
        ReflectionTestUtils.setField(p, "id", 7L);
        when(repository.findByUsername("alice")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_failsWithUnknownUsername() {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "x")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
