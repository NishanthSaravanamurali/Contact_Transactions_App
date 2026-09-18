package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.ChangePasswordRequest;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.ConcurrentUpdateException;
import com.contacttx.userservice.exception.InactiveUserException;
import com.contacttx.userservice.exception.InvalidCurrentPasswordException;
import com.contacttx.userservice.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPasswordServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserPasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new UserPasswordService(userRepository, passwordEncoder);
    }

    @Test
    void verifiesAndHashesTheNewPasswordBeforeUpdating() {
        AppUser user = user(UserStatus.ACTIVE);
        LocalDateTime timestamp = user.getUpdatedAt();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Old@Password123", "stored-argon2-hash"))
                .thenReturn(true);
        when(passwordEncoder.encode("New@Password456")).thenReturn("new-argon2-hash");
        when(userRepository.updatePasswordIfUnchanged(
                42L, UserStatus.ACTIVE, timestamp, "new-argon2-hash"))
                .thenReturn(1);

        passwordService.changePassword(
                42L,
                new ChangePasswordRequest("Old@Password123", "New@Password456"));

        verify(passwordEncoder).matches("Old@Password123", "stored-argon2-hash");
        verify(passwordEncoder).encode("New@Password456");
        verify(userRepository).updatePasswordIfUnchanged(
                42L, UserStatus.ACTIVE, timestamp, "new-argon2-hash");
    }

    @Test
    void rejectsAnIncorrectCurrentPasswordWithoutHashingTheNewOne() {
        AppUser user = user(UserStatus.ACTIVE);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong@Password123", "stored-argon2-hash"))
                .thenReturn(false);

        assertThrows(InvalidCurrentPasswordException.class,
                () -> passwordService.changePassword(
                        42L,
                        new ChangePasswordRequest(
                                "Wrong@Password123", "New@Password456")));

        verify(passwordEncoder, never()).encode("New@Password456");
        verify(userRepository, never()).updatePasswordIfUnchanged(
                42L, UserStatus.ACTIVE, user.getUpdatedAt(), "new-argon2-hash");
    }

    @Test
    void rejectsAnInactiveUser() {
        AppUser user = user(UserStatus.INACTIVE);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThrows(InactiveUserException.class,
                () -> passwordService.changePassword(
                        42L,
                        new ChangePasswordRequest(
                                "Old@Password123", "New@Password456")));

        verify(passwordEncoder, never()).matches(
                "Old@Password123", "stored-argon2-hash");
    }

    @Test
    void reportsAConflictWhenTheUserChangesConcurrently() {
        AppUser user = user(UserStatus.ACTIVE);
        LocalDateTime timestamp = user.getUpdatedAt();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Old@Password123", "stored-argon2-hash"))
                .thenReturn(true);
        when(passwordEncoder.encode("New@Password456")).thenReturn("new-argon2-hash");
        when(userRepository.updatePasswordIfUnchanged(
                42L, UserStatus.ACTIVE, timestamp, "new-argon2-hash"))
                .thenReturn(0);

        assertThrows(ConcurrentUpdateException.class,
                () -> passwordService.changePassword(
                        42L,
                        new ChangePasswordRequest(
                                "Old@Password123", "New@Password456")));
    }

    private AppUser user(UserStatus status) {
        AppUser user = new AppUser(
                "Alex Johnson",
                "stored-argon2-hash",
                "alex@example.com",
                9876543210L,
                LocalDate.of(2000, 6, 15),
                status);
        ReflectionTestUtils.setField(user, "userId", 42L);
        ReflectionTestUtils.setField(
                user, "updatedAt", LocalDateTime.of(2026, 9, 16, 10, 0));
        return user;
    }
}
