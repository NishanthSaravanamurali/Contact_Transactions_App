package com.contacttx.userservice.service;

import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.ConcurrentUpdateException;
import com.contacttx.userservice.exception.InactiveUserException;
import com.contacttx.userservice.exception.UserNotFoundException;
import com.contacttx.userservice.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeactivationServiceTest {

    @Mock
    private AppUserRepository userRepository;

    private UserDeactivationService deactivationService;

    @BeforeEach
    void setUp() {
        deactivationService = new UserDeactivationService(userRepository);
    }

    @Test
    void changesAnActiveUserToInactiveWithoutDeletingTheRow() {
        AppUser user = user(UserStatus.ACTIVE);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.updateStatusIfUnchanged(
                42L,
                UserStatus.ACTIVE,
                UserStatus.INACTIVE,
                user.getUpdatedAt()))
                .thenReturn(1);

        deactivationService.deactivate(42L);

        verify(userRepository).updateStatusIfUnchanged(
                42L,
                UserStatus.ACTIVE,
                UserStatus.INACTIVE,
                user.getUpdatedAt());
    }

    @Test
    void rejectsAUserThatDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> deactivationService.deactivate(42L));
    }

    @Test
    void rejectsAnAlreadyInactiveUser() {
        when(userRepository.findById(42L))
                .thenReturn(Optional.of(user(UserStatus.INACTIVE)));

        assertThrows(InactiveUserException.class,
                () -> deactivationService.deactivate(42L));
    }

    @Test
    void reportsAConflictWhenTheRowChangesConcurrently() {
        AppUser user = user(UserStatus.ACTIVE);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.updateStatusIfUnchanged(
                42L,
                UserStatus.ACTIVE,
                UserStatus.INACTIVE,
                user.getUpdatedAt()))
                .thenReturn(0);

        assertThrows(ConcurrentUpdateException.class,
                () -> deactivationService.deactivate(42L));
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
