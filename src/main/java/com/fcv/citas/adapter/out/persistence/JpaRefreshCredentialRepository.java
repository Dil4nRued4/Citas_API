package com.fcv.citas.adapter.out.persistence;

import com.fcv.citas.application.port.out.RefreshCredentialRepository;
import com.fcv.citas.domain.RefreshCredential;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaRefreshCredentialRepository implements RefreshCredentialRepository {
    private final SpringDataRefreshTokenRepository tokens;
    private final SpringDataUserRepository users;

    public JpaRefreshCredentialRepository(
            SpringDataRefreshTokenRepository tokens,
            SpringDataUserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    @Transactional
    public void save(RefreshCredential credential) {
        UserEntity user = users.findById(credential.userId())
                .orElseThrow(() -> new IllegalStateException("Refresh token owner no longer exists"));
        tokens.save(new RefreshTokenEntity(user, credential.tokenHash(), credential.expiresAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshCredential> findByHash(String tokenHash) {
        return tokens.findByTokenHash(tokenHash)
                .map(token -> new RefreshCredential(
                        token.getUserId(),
                        token.getTokenHash(),
                        token.getExpiresAt(),
                        token.getRevokedAt()));
    }

    @Override
    @Transactional
    public void revoke(String tokenHash, Instant revokedAt) {
        tokens.findByTokenHash(tokenHash).ifPresent(token -> token.revoke(revokedAt));
    }
}
