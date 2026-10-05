package com.fcv.citas.adapter.out.persistence;

import com.fcv.citas.application.port.out.AccountRepository;
import com.fcv.citas.domain.UserAccount;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaAccountRepository implements AccountRepository {
    private final SpringDataUserRepository users;

    public JpaAccountRepository(SpringDataUserRepository users) {
        this.users = users;
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.existsByEmail(email);
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return users.findByEmail(email).map(JpaAccountRepository::toAccount);
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        return users.findById(id).map(JpaAccountRepository::toAccount);
    }

    @Override
    public UserAccount create(String name, String email, String passwordHash) {
        return toAccount(users.save(new UserEntity(name, email, passwordHash)));
    }

    private static UserAccount toAccount(UserEntity entity) {
        return new UserAccount(
                entity.getId(), entity.getName(), entity.getEmail(), entity.getPasswordHash(), entity.getRole());
    }
}
