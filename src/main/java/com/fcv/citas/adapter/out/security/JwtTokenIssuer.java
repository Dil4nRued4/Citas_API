package com.fcv.citas.adapter.out.security;

import com.fcv.citas.application.port.out.TokenIssuer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenIssuer implements TokenIssuer {
    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final Clock clock;

    public JwtTokenIssuer(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-ttl-seconds}") long accessTtlSeconds,
            @Value("${app.jwt.refresh-ttl-seconds}") long refreshTtlSeconds,
            Clock clock) {
        byte[] keyBytes = secret.startsWith("base64:")
                ? Decoders.BASE64.decode(secret.substring("base64:".length()))
                : secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.clock = clock;
    }

    @Override
    public IssuedToken issueAccessToken(long userId) {
        return issue(userId, "access", accessTtlSeconds);
    }

    @Override
    public IssuedToken issueRefreshToken(long userId) {
        return issue(userId, "refresh", refreshTtlSeconds);
    }

    @Override
    public Optional<Long> readRefreshSubject(String token) {
        try {
            Claims claims = parse(token);
            if (!"refresh".equals(claims.get("type", String.class))) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Optional<Long> readAccessSubject(String token) {
        try {
            Claims claims = parse(token);
            if (!"access".equals(claims.get("type", String.class))) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    @Override
    public long accessLifetimeSeconds() {
        return accessTtlSeconds;
    }

    private IssuedToken issue(long userId, String type, long ttlSeconds) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String value = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(Long.toString(userId))
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(value, expiresAt);
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
