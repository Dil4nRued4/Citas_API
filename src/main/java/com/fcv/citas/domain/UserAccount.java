package com.fcv.citas.domain;

public record UserAccount(long id, String name, String email, String passwordHash, String role) {
}
