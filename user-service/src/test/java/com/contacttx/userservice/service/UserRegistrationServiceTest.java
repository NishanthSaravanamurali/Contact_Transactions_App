package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.RegisterUserRequest;
import com.contacttx.userservice.dto.response.RegistrationResponse;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.DuplicateEmailException;
import com.contacttx.userservice.mapper.UserMapper;
import com.contacttx.userservice.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UserRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new UserRegistrationService(
                userRepository,
                passwordEncoder,
                new UserMapper(),
                eventPublisher);
    }

    @Test
    void registersAnActiveUserWithNormalizedEmailAndHashedPassword() {
        RegisterUserRequest request = validRequest("  Alex@Example.COM  ");
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Strong@Password123")).thenReturn("argon2-hash");
        when(userRepository.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation -> {
                    AppUser user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "userId", 42L);
                    return user;
                });

        RegistrationResponse response = registrationService.register(request);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        AppUser savedUser = userCaptor.getValue();
        ArgumentCaptor<UserRegisteredEvent> eventCaptor =
                ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertEquals("alex@example.com", savedUser.getEmail());
        assertEquals("argon2-hash", savedUser.getPasswordHash());
        assertEquals(9876543210L, savedUser.getMobileNo());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertEquals("alex@example.com", response.getEmail());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        assertEquals(42L, eventCaptor.getValue().getUserId());
    }

    @Test
    void rejectsAnEmailAlreadyFoundByTheApplicationCheck() {
        RegisterUserRequest request = validRequest("alex@example.com");
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class,
                () -> registrationService.register(request));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesAnOracleUniqueConstraintRaceToDuplicateEmail() {
        RegisterUserRequest request = validRequest("alex@example.com");
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Strong@Password123")).thenReturn("argon2-hash");
        when(userRepository.saveAndFlush(any(AppUser.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "ORA-00001: unique constraint (SYSTEM.UQ_APP_USER_EMAIL) violated"));

        DuplicateEmailException exception = assertThrows(
                DuplicateEmailException.class,
                () -> registrationService.register(request));

        assertTrue(exception.getErrorCode().name().contains("DUPLICATE_EMAIL"));
    }

    private RegisterUserRequest validRequest(String email) {
        return new RegisterUserRequest(
                "Alex Johnson",
                email,
                "Strong@Password123",
                "9876543210",
                LocalDate.now().minusYears(25));
    }
}
