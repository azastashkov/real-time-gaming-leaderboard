package com.realtimegaming.leaderboard.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-1234567890";

    @Test
    void generated_tokenContainsExpectedClaims() {
        JwtTokenService svc = new JwtTokenService(encoder(), Duration.ofMinutes(15), "test-issuer");

        String token = svc.generate(42L, "alice");
        Jwt decoded = decoder().decode(token);

        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsString("username")).isEqualTo("alice");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("test-issuer");
        assertThat(decoded.getExpiresAt()).isNotNull();
        assertThat(decoded.getIssuedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void expiresAt_isInTheFuture() {
        JwtTokenService svc = new JwtTokenService(encoder(), Duration.ofMinutes(30), "iss");
        Instant exp = svc.expiresAt();
        assertThat(exp).isAfter(Instant.now());
    }

    private static NimbusJwtEncoder encoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(SECRET.getBytes(StandardCharsets.UTF_8)));
    }

    private static JwtDecoder decoder() {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
