package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.ResolveUserRequest;
import com.contacttx.userservice.dto.request.DisplayNamesRequest;
import com.contacttx.userservice.dto.response.InternalUserStatusResponse;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.UserNotFoundException;
import com.contacttx.userservice.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    private InternalUserService internalUserService;

    @BeforeEach
    void setUp() {
        internalUserService = new InternalUserService(userRepository);
    }

    @Test
    void returnsTheCurrentStatusForAUserId() {
        when(userRepository.findById(42L))
                .thenReturn(Optional.of(user(UserStatus.INACTIVE)));

        InternalUserStatusResponse response = internalUserService.getStatus(42L);

        assertEquals(42L, response.getUserId());
        assertEquals(UserStatus.INACTIVE, response.getStatus());
    }

    @Test
    void resolvesAUserByTheNumericOracleMobileValue() {
        when(userRepository.findFirstByMobileNoOrderByUserIdAsc(9876543210L))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        InternalUserStatusResponse response = internalUserService.resolveByMobile(
                new ResolveUserRequest("9876543210"));

        assertEquals(42L, response.getUserId());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        verify(userRepository).findFirstByMobileNoOrderByUserIdAsc(9876543210L);
    }

    @Test
    void reportsNotFoundForAnUnknownMobileNumber() {
        when(userRepository.findFirstByMobileNoOrderByUserIdAsc(9876543210L))
                .thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> internalUserService.resolveByMobile(
                        new ResolveUserRequest("9876543210")));
    }

    @Test
    void resolvesExistingNonDeletedNamesAndReportsMissingIds() {
        var alice = projection(7L, "Alice Sharma");
        var bob = projection(12L, "Bob Tester");
        when(userRepository.findDisplayNamesByUserIds(
                List.of(7L, 12L, 25L), UserStatus.DELETED))
                .thenReturn(List.of(bob, alice));

        var response = internalUserService.resolveDisplayNames(
                new DisplayNamesRequest(List.of(7L, 12L, 7L, 25L)));

        assertEquals(List.of(7L, 12L), response.users().stream()
                .map(user -> user.userId()).toList());
        assertEquals(List.of("Alice Sharma", "Bob Tester"), response.users().stream()
                .map(user -> user.displayName()).toList());
        assertEquals(List.of(25L), response.unresolvedUserIds());
        verify(userRepository).findDisplayNamesByUserIds(
                List.of(7L, 12L, 25L), UserStatus.DELETED);
    }

    private AppUserRepository.UserDisplayNameProjection projection(Long userId, String name) {
        return new AppUserRepository.UserDisplayNameProjection() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getName() {
                return name;
            }
        };
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
