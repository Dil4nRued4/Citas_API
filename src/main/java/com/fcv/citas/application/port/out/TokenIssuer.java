package com.fcv.citas.application.port.out;

import java.time.Instant;
import java.util.Optional;

public interface TokenIssuer {
    IssuedToken issueAccessToken(long userId);

    IssuedToken issueRefreshToken(long userId);

    Optional<Long> readRefreshSubject(String token);

    String fingerprint(String token);

    long accessLifetimeSeconds();

    record IssuedToken(String value, Instant expiresAt) {
    }
}
