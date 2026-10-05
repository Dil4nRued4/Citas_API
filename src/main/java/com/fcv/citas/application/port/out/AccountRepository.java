package com.fcv.citas.application.port.out;

import com.fcv.citas.domain.UserAccount;

import java.util.Optional;

public interface AccountRepository {
    boolean existsByEmail(String email);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findById(long id);

    UserAccount create(String name, String email, String passwordHash);
}
