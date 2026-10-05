package com.fcv.citas.domain;

import java.time.Instant;

public record RefreshCredential(long userId, String tokenHash, Instant expiresAt, Instant revokedAt) {
    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
