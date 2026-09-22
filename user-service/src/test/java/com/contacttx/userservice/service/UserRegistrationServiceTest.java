package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.RegisterUserRequest;
import com.contacttx.userservice.dto.response.RegistrationResponse;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.OutboxEvent;
import com.contacttx.userservice.entity.UserStatus;
import com.contacttx.userservice.exception.DuplicateEmailException;
import com.contacttx.userservice.mapper.UserMapper;
import com.contacttx.userservice.messaging.OutboxEventFactory;
import com.contacttx.userservice.repository.AppUserRepository;
import com.contacttx.userservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

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
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new UserRegistrationService(
                userRepository,
                outboxEventRepository,
                passwordEncoder,
                new UserMapper(),
                new OutboxEventFactory(new ObjectMapper()));
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
        ArgumentCaptor<OutboxEvent> outboxCaptor =
                ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outboxEvent = outboxCaptor.getValue();

        assertEquals("alex@example.com", savedUser.getEmail());
        assertEquals("argon2-hash", savedUser.getPasswordHash());
        assertEquals(9876543210L, savedUser.getMobileNo());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertEquals("alex@example.com", response.getEmail());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        UUID.fromString(outboxEvent.getEventId());
        assertEquals(OutboxEventFactory.USER_REGISTERED, outboxEvent.getEventType());
        assertEquals(42L, outboxEvent.getAggregateId());
        assertEquals(0, outboxEvent.getPublishAttempts());
        assertEquals(null, outboxEvent.getPublishedAt());
        assertEquals(null, outboxEvent.getLastError());
        assertTrue(outboxEvent.getPayload().contains("\"userId\":42"));
        assertTrue(outboxEvent.getPayload().contains("\"eventVersion\":1"));
        assertTrue(!outboxEvent.getPayload().contains("alex@example.com"));
        assertTrue(!outboxEvent.getPayload().contains("Strong@Password123"));
    }

    @Test
    void rejectsAnEmailAlreadyFoundByTheApplicationCheck() {
        RegisterUserRequest request = validRequest("alex@example.com");
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class,
                () -> registrationService.register(request));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any());
        verify(outboxEventRepository, never()).save(any());
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
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void propagatesOutboxPersistenceFailureSoTheRegistrationTransactionRollsBack() {
        RegisterUserRequest request = validRequest("alex@example.com");
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Strong@Password123")).thenReturn("argon2-hash");
        when(userRepository.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation -> {
                    AppUser user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "userId", 42L);
                    return user;
                });
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenThrow(new DataIntegrityViolationException("outbox insert failed"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> registrationService.register(request));
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
