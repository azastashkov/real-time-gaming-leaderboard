package com.realtimegaming.leaderboard.auth;

import com.realtimegaming.leaderboard.dto.LoginRequest;
import com.realtimegaming.leaderboard.dto.LoginResponse;
import com.realtimegaming.leaderboard.dto.RegisterRequest;
import com.realtimegaming.leaderboard.player.Player;
import com.realtimegaming.leaderboard.player.PlayerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final PlayerRepository repository;
    private final PasswordEncoder encoder;
    private final JwtTokenService jwt;

    public AuthService(PlayerRepository repository, PasswordEncoder encoder, JwtTokenService jwt) {
        this.repository = repository;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (repository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }
        Player p = new Player(request.username(), encoder.encode(request.password()));
        repository.save(p);
        Instant exp = jwt.expiresAt();
        return LoginResponse.bearer(jwt.generate(p.getId(), p.getUsername()), exp, p.getId(), p.getUsername());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Player p = repository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);
        if (!encoder.matches(request.password(), p.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        Instant exp = jwt.expiresAt();
        return LoginResponse.bearer(jwt.generate(p.getId(), p.getUsername()), exp, p.getId(), p.getUsername());
    }
}
