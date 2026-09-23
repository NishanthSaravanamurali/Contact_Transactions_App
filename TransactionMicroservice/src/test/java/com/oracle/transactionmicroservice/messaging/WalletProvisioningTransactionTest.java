package com.oracle.transactionmicroservice.messaging;

import com.oracle.transactionmicroservice.entity.Wallet;
import com.oracle.transactionmicroservice.policy.MoneyPolicy;
import com.oracle.transactionmicroservice.repository.AccountRepository;
import com.oracle.transactionmicroservice.repository.TransactionRepository;
import com.oracle.transactionmicroservice.repository.WalletRepository;
import com.oracle.transactionmicroservice.service.abstractions.WalletCommandService;
import com.oracle.transactionmicroservice.service.implementations.GuardedLocalTransaction;
import com.oracle.transactionmicroservice.service.implementations.WalletCommandServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletProvisioningTransactionTest {

    @Test
    void duplicateDeliveryCommitsSuccessfullyAndCreatesOnlyOneWallet() {
        WalletRepository repository = mock(WalletRepository.class);
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager();
        WalletCommandService service = transactionalService(
                repository, transactionManager);

        Wallet persisted = preparedWallet(41L);
        when(repository.findByUserId(41L))
                .thenReturn(Optional.empty(), Optional.of(persisted));
        when(repository.saveAndFlush(any(Wallet.class)))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return preparePersisted(invocation.getArgument(0));
                });

        service.createWallet(41L);
        service.createWallet(41L);

        verify(repository, times(1)).saveAndFlush(any(Wallet.class));
        assertEquals(2, transactionManager.commits);
        assertEquals(0, transactionManager.rollbacks);
    }

    @Test
    void temporaryDatabaseFailureRollsBackAndEscapesForKafkaRetry() {
        WalletRepository repository = mock(WalletRepository.class);
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager();
        WalletCommandService service = transactionalService(
                repository, transactionManager);

        when(repository.findByUserId(41L)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Wallet.class)))
                .thenThrow(new QueryTimeoutException("temporary database timeout"));

        assertThrows(QueryTimeoutException.class, () -> service.createWallet(41L));
        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
    }

    private WalletCommandService transactionalService(
            WalletRepository repository,
            RecordingTransactionManager transactionManager
    ) {
        WalletCommandServiceImpl target = new WalletCommandServiceImpl(
                mock(AccountRepository.class),
                repository,
                mock(TransactionRepository.class),
                new MoneyPolicy(),
                mock(GuardedLocalTransaction.class));

        ProxyFactory proxyFactory = new ProxyFactory(target);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(
                new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        proxyFactory.addAdvice(interceptor);
        return (WalletCommandService) proxyFactory.getProxy();
    }

    private Wallet preparedWallet(Long userId) {
        return preparePersisted(new Wallet(userId));
    }

    private Wallet preparePersisted(Wallet wallet) {
        ReflectionTestUtils.setField(wallet, "walletId", 100L);
        ReflectionTestUtils.setField(wallet, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(wallet, "updatedAt", LocalDateTime.now());
        return wallet;
    }

    private static class RecordingTransactionManager
            extends AbstractPlatformTransactionManager {

        private int commits;
        private int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
