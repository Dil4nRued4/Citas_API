package com.fcv.citas.application;

import com.fcv.citas.application.port.out.AccountRepository;
import com.fcv.citas.application.port.out.PasswordHasher;
import com.fcv.citas.application.port.out.RefreshCredentialRepository;
import com.fcv.citas.application.port.out.TokenIssuer;
import com.fcv.citas.domain.RefreshCredential;
import com.fcv.citas.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class AuthService {
    private final AccountRepository accounts;
    private final RefreshCredentialRepository refreshCredentials;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    public AuthService(
            AccountRepository accounts,
            RefreshCredentialRepository refreshCredentials,
            PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer,
            Clock clock) {
        this.accounts = accounts;
        this.refreshCredentials = refreshCredentials;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Transactional
    public UserAccount register(String name, String email, String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRegistrationException();
        }
        String normalizedEmail = normalizeEmail(email);
        if (accounts.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException();
        }
        return accounts.create(name.trim(), normalizedEmail, passwordHasher.hash(rawPassword));
    }

    @Transactional
    public TokenPair login(String email, String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidCredentialsException();
        }
        UserAccount account = accounts.findByEmail(normalizeEmail(email))
                .filter(candidate -> passwordHasher.matches(rawPassword, candidate.passwordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return issueTokenPair(account);
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        String fingerprint = tokenIssuer.fingerprint(refreshToken);
        Instant now = clock.instant();
        RefreshCredential current = refreshCredentials.findByHash(fingerprint)
                .filter(credential -> credential.isActiveAt(now))
                .orElseThrow(InvalidCredentialsException::new);
        long tokenUserId = tokenIssuer.readRefreshSubject(refreshToken)
                .orElseThrow(InvalidCredentialsException::new);
        if (tokenUserId != current.userId()) {
            throw new InvalidCredentialsException();
        }
        UserAccount account = accounts.findById(current.userId())
                .orElseThrow(InvalidCredentialsException::new);
        refreshCredentials.revoke(fingerprint, now);
        return issueTokenPair(account);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String fingerprint = tokenIssuer.fingerprint(refreshToken);
        Optional<RefreshCredential> credential = refreshCredentials.findByHash(fingerprint);
        if (credential.isPresent() && credential.get().revokedAt() == null) {
            refreshCredentials.revoke(fingerprint, clock.instant());
        }
    }

    @Transactional(readOnly = true)
    public UserAccount findById(long id) {
        return accounts.findById(id).orElseThrow(InvalidCredentialsException::new);
    }

    private TokenPair issueTokenPair(UserAccount account) {
        TokenIssuer.IssuedToken access = tokenIssuer.issueAccessToken(account.id());
        TokenIssuer.IssuedToken refresh = tokenIssuer.issueRefreshToken(account.id());
        refreshCredentials.save(new RefreshCredential(
                account.id(),
                tokenIssuer.fingerprint(refresh.value()),
                refresh.expiresAt(),
                null));
        return new TokenPair(access.value(), refresh.value(), tokenIssuer.accessLifetimeSeconds());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
    }

    public static class EmailAlreadyUsedException extends RuntimeException {
    }

    public static class InvalidCredentialsException extends RuntimeException {
    }

    public static class InvalidRegistrationException extends RuntimeException {
    }
}
