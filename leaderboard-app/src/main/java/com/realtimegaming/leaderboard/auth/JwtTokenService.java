package com.realtimegaming.leaderboard.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final String issuer;

    public JwtTokenService(JwtEncoder encoder,
                           @Value("${jwt.ttl:PT1H}") Duration ttl,
                           @Value("${jwt.issuer:leaderboard-app}") String issuer) {
        this.encoder = encoder;
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public String generate(long playerId, String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(String.valueOf(playerId))
                .claim("username", username)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Instant expiresAt() {
        return Instant.now().plus(ttl);
    }
}
