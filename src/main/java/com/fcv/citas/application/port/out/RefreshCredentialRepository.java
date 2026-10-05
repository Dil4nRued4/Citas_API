package com.fcv.citas.application.port.out;

import com.fcv.citas.domain.RefreshCredential;

import java.time.Instant;
import java.util.Optional;

public interface RefreshCredentialRepository {
    void save(RefreshCredential credential);

    Optional<RefreshCredential> findByHash(String tokenHash);

    void revoke(String tokenHash, Instant revokedAt);
}
