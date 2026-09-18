package com.contacttx.userservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordConfigTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createPasswordEncoder() {
        passwordEncoder = new PasswordConfig().passwordEncoder();
    }

    @Test
    void hashesAndVerifiesPasswordWithArgon2id() {
        String password = "Strong@Password123";

        String encodedPassword = passwordEncoder.encode(password);

        assertThat(encodedPassword).isNotEqualTo(password);
        assertThat(encodedPassword).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches(password, encodedPassword)).isTrue();
        assertThat(passwordEncoder.matches("Incorrect@Password123", encodedPassword)).isFalse();
    }

    @Test
    void usesDifferentSaltForEveryEncoding() {
        String password = "Strong@Password123";

        String firstEncoding = passwordEncoder.encode(password);
        String secondEncoding = passwordEncoder.encode(password);

        assertThat(firstEncoding).isNotEqualTo(secondEncoding);
        assertThat(passwordEncoder.matches(password, firstEncoding)).isTrue();
        assertThat(passwordEncoder.matches(password, secondEncoding)).isTrue();
    }
}
