package com.contacttx.userservice.service;

import com.contacttx.userservice.dto.request.RegisterUserRequest;
import com.contacttx.userservice.entity.AppUser;
import com.contacttx.userservice.entity.OutboxEvent;
import com.contacttx.userservice.mapper.UserMapper;
import com.contacttx.userservice.messaging.OutboxEventFactory;
import com.contacttx.userservice.repository.AppUserRepository;
import com.contacttx.userservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserRegistrationTransactionTest {

    @Test
    void savesTheUserAndOutboxEventInsideOneCommittedTransaction() {
        TestFixture fixture = fixture(false);

        fixture.transactionalService.register(validRequest());

        assertTrue(fixture.userSavedInsideTransaction);
        assertTrue(fixture.outboxSavedInsideTransaction);
        assertTrue(fixture.transactionManager.committed);
        assertFalse(fixture.transactionManager.rolledBack);
    }

    @Test
    void rollsBackTheTransactionWhenTheOutboxInsertFails() {
        TestFixture fixture = fixture(true);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> fixture.transactionalService.register(validRequest()));

        assertTrue(fixture.userSavedInsideTransaction);
        assertTrue(fixture.outboxSavedInsideTransaction);
        assertFalse(fixture.transactionManager.committed);
        assertTrue(fixture.transactionManager.rolledBack);
    }

    private TestFixture fixture(boolean failOutboxInsert) {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        OutboxEventRepository outboxEventRepository =
                mock(OutboxEventRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager();
        TestFixture fixture = new TestFixture(transactionManager);

        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Strong@Password123")).thenReturn("argon2-hash");
        when(userRepository.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation -> {
                    fixture.userSavedInsideTransaction =
                            TransactionSynchronizationManager
                                    .isActualTransactionActive();
                    AppUser user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "userId", 42L);
                    return user;
                });

        OutboxEvent event = new OutboxEvent(
                "62ed213e-faf0-43bf-9f0f-0b932eae5ee9",
                OutboxEventFactory.USER_REGISTERED,
                42L,
                "{\"payload\":{\"userId\":42}}",
                LocalDateTime.of(2026, 9, 22, 10, 0));
        when(outboxEventFactory.createUserRegistered(42L)).thenReturn(event);
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> {
                    fixture.outboxSavedInsideTransaction =
                            TransactionSynchronizationManager
                                    .isActualTransactionActive();
                    if (failOutboxInsert) {
                        throw new DataIntegrityViolationException(
                                "outbox insert failed");
                    }
                    return invocation.getArgument(0);
                });

        UserRegistrationService target = new UserRegistrationService(
                userRepository,
                outboxEventRepository,
                passwordEncoder,
                new UserMapper(),
                outboxEventFactory);

        ProxyFactory proxyFactory = new ProxyFactory(target);
        TransactionInterceptor transactionInterceptor =
                new TransactionInterceptor();
        transactionInterceptor.setTransactionManager(transactionManager);
        transactionInterceptor.setTransactionAttributeSource(
                new AnnotationTransactionAttributeSource());
        transactionInterceptor.afterPropertiesSet();
        proxyFactory.addAdvice(transactionInterceptor);
        fixture.transactionalService =
                (UserRegistrationService) proxyFactory.getProxy();
        return fixture;
    }

    private RegisterUserRequest validRequest() {
        return new RegisterUserRequest(
                "Alex Johnson",
                "alex@example.com",
                "Strong@Password123",
                "9876543210",
                LocalDate.now().minusYears(25));
    }

    private static class TestFixture {

        private final RecordingTransactionManager transactionManager;
        private UserRegistrationService transactionalService;
        private boolean userSavedInsideTransaction;
        private boolean outboxSavedInsideTransaction;

        private TestFixture(RecordingTransactionManager transactionManager) {
            this.transactionManager = transactionManager;
        }
    }

    private static class RecordingTransactionManager
            extends AbstractPlatformTransactionManager {

        private boolean committed;
        private boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition) {
            // No external resource is needed for this transaction-boundary test.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
