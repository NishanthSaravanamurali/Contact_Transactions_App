package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.UpdateProfileRequest;
import com.contacttx.userservice.dto.response.UserResponse;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.InactiveUserException;
import com.contacttx.userservice.exception.ConcurrentUpdateException;
import com.contacttx.userservice.exception.UserNotFoundException;
import com.contacttx.userservice.mapper.UserMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private AppUserRepository userRepository;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userRepository, new UserMapper());
    }

    @Test
    void returnsTheSafeProfileForAnActiveUser() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        UserResponse response = userProfileService.getProfile(42L);

        assertEquals(42L, response.getUserId());
        assertEquals("Alex Johnson", response.getName());
        assertEquals("alex@example.com", response.getEmail());
        assertEquals("9876543210", response.getMobileNo());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
    }

    @Test
    void returnsNotFoundWhenTheAuthenticatedUserRowDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userProfileService.getProfile(42L));
    }

    @Test
    void rejectsAnInactiveUserEvenWhenTheirJwtIsStillValid() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user(UserStatus.INACTIVE)));

        assertThrows(InactiveUserException.class,
                () -> userProfileService.getProfile(42L));
    }

    @Test
    void updatesOnlyTheProvidedFieldAndReturnsTheReloadedProfile() {
        AppUser currentUser = user(UserStatus.ACTIVE);
        LocalDateTime originalTimestamp = LocalDateTime.of(2026, 9, 16, 10, 0);
        ReflectionTestUtils.setField(currentUser, "updatedAt", originalTimestamp);
        AppUser updatedUser = user(UserStatus.ACTIVE);
        updatedUser.setMobileNo(9123456789L);
        when(userRepository.findById(42L))
                .thenReturn(Optional.of(currentUser))
                .thenReturn(Optional.of(updatedUser));
        when(userRepository.updateProfileIfUnchanged(
                42L,
                UserStatus.ACTIVE,
                originalTimestamp,
                "Alex Johnson",
                9123456789L))
                .thenReturn(1);

        UserResponse response = userProfileService.updateProfile(
                42L, new UpdateProfileRequest(null, "9123456789"));

        assertEquals("Alex Johnson", response.getName());
        assertEquals("9123456789", response.getMobileNo());
        verify(userRepository).updateProfileIfUnchanged(
                42L,
                UserStatus.ACTIVE,
                originalTimestamp,
                "Alex Johnson",
                9123456789L);
    }

    @Test
    void reportsAConflictWhenTheConditionalUpdateChangesNoRows() {
        AppUser currentUser = user(UserStatus.ACTIVE);
        LocalDateTime originalTimestamp = LocalDateTime.of(2026, 9, 16, 10, 0);
        ReflectionTestUtils.setField(currentUser, "updatedAt", originalTimestamp);
        when(userRepository.findById(42L)).thenReturn(Optional.of(currentUser));
        when(userRepository.updateProfileIfUnchanged(
                42L,
                UserStatus.ACTIVE,
                originalTimestamp,
                "Alex Updated",
                9876543210L))
                .thenReturn(0);

        assertThrows(ConcurrentUpdateException.class,
                () -> userProfileService.updateProfile(
                        42L, new UpdateProfileRequest("Alex Updated", null)));
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
        return user;
    }
}
