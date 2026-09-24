package com.oracle.transactionmicroservice.service;

import com.oracle.transactionmicroservice.dto.request.AddFundsRequest;
import com.oracle.transactionmicroservice.dto.request.MakePaymentRequest;
import com.oracle.transactionmicroservice.entity.Account;
import com.oracle.transactionmicroservice.entity.Transaction;
import com.oracle.transactionmicroservice.entity.Wallet;
import com.oracle.transactionmicroservice.enums.AccountStatus;
import com.oracle.transactionmicroservice.enums.TransactionStatus;
import com.oracle.transactionmicroservice.enums.TransactionType;
import com.oracle.transactionmicroservice.exception.ForbiddenOperationException;
import com.oracle.transactionmicroservice.exception.InvalidMoneyException;
import com.oracle.transactionmicroservice.exception.ResourceNotFoundException;
import com.oracle.transactionmicroservice.policy.MoneyPolicy;
import com.oracle.transactionmicroservice.repository.AccountRepository;
import com.oracle.transactionmicroservice.repository.TransactionRepository;
import com.oracle.transactionmicroservice.repository.WalletRepository;
import com.oracle.transactionmicroservice.service.abstractions.UserOperationGuard;
import com.oracle.transactionmicroservice.service.implementations.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServiceLayerTests {
    private AccountRepository accounts;
    private WalletRepository wallets;
    private TransactionRepository transactions;
    private TrackingTransactionManager transactionManager;
    private GuardedLocalTransaction localTransaction;
    private WalletCommandServiceImpl service;
    private boolean guardHeld;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        wallets = mock(WalletRepository.class);
        transactions = mock(TransactionRepository.class);
        transactionManager = new TrackingTransactionManager();
        UserOperationGuard guard = new UserOperationGuard() {
            @Override
            public <T> T withActiveUsers(Long current, Long recipient, Supplier<T> action) {
                guardHeld = true;
                try {
                    return action.get();
                } finally {
                    guardHeld = false;
                }
            }
        };
        localTransaction = new GuardedLocalTransaction(guard, transactionManager);
        service = new WalletCommandServiceImpl(
                accounts, wallets, transactions, new MoneyPolicy(), localTransaction);
    }

    @Test
    void topUpMovesMoneyAndCompletesWithinGuardedTransaction() {
        Account source = account(1L, "100.00");
        Wallet destination = wallet(1L, "5.00");
        when(accounts.findByAccountIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(source));
        when(wallets.findByUserIdForUpdate(1L)).thenReturn(Optional.of(destination));
        recordTransactions();
        var result = service.addFunds(1L, new AddFundsRequest(10L, money("20.00")));
        assertEquals(money("80.00"), source.getBalance());
        assertEquals(money("25.00"), destination.getBalance());
        assertEquals(TransactionStatus.COMPLETED, result.status());
        assertNotNull(result.completedAt());
        assertNull(result.sourceWalletId());
        assertEquals(1L, result.destinationUserId());
        assertEquals(1, transactionManager.commits);
        assertFalse(guardHeld);
        var order = inOrder(accounts, wallets);
        order.verify(accounts).findByAccountIdAndUserIdForUpdate(10L, 1L);
        order.verify(wallets).findByUserIdForUpdate(1L);
    }

    @Test
    void insufficientFundsRecordsFailedWithoutChangingBalances() {
        Account source = account(1L, "5.00");
        Wallet destination = wallet(1L, "7.00");
        when(accounts.findByAccountIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(source));
        when(wallets.findByUserIdForUpdate(1L)).thenReturn(Optional.of(destination));
        recordTransactions();
        var result = service.addFunds(1L, new AddFundsRequest(10L, money("6.00")));
        assertEquals(TransactionStatus.FAILED, result.status());
        assertNull(result.completedAt());
        assertEquals(money("5.00"), source.getBalance());
        assertEquals(money("7.00"), destination.getBalance());
        assertEquals(1, transactionManager.commits);
    }

    @Test
    void reverseDirectionPaymentStillLocksUsersInAscendingOrder() {
        Wallet first = wallet(1L, "2.00");
        Wallet second = wallet(2L, "50.00");
        when(wallets.findByUserIdForUpdate(1L)).thenReturn(Optional.of(first));
        when(wallets.findByUserIdForUpdate(2L)).thenReturn(Optional.of(second));
        recordTransactions();
        var result = service.makePayment(2L, new MakePaymentRequest(1L, money("10.00")));
        assertEquals(money("12.00"), first.getBalance());
        assertEquals(money("40.00"), second.getBalance());
        assertEquals(TransactionStatus.COMPLETED, result.status());
        assertNull(result.sourceAccountId());
        assertEquals(1L, result.destinationUserId());
        var order = inOrder(wallets);
        order.verify(wallets).findByUserIdForUpdate(1L);
        order.verify(wallets).findByUserIdForUpdate(2L);
    }

    @Test
    void overflowIsRejectedBeforeAnyBalanceMutation() {
        Account source = account(1L, "10.00");
        Wallet destination = wallet(1L, "999999999999999999.99");
        when(accounts.findByAccountIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(source));
        when(wallets.findByUserIdForUpdate(1L)).thenReturn(Optional.of(destination));
        assertThrows(InvalidMoneyException.class,
                () -> service.addFunds(1L, new AddFundsRequest(10L, money("1.00"))));
        assertEquals(money("10.00"), source.getBalance());
        assertEquals(money("999999999999999999.99"), destination.getBalance());
        verifyNoInteractions(transactions);
        assertEquals(1, transactionManager.rollbacks);
    }

    @Test
    void technicalFailureRequestsRollbackAndDoesNotRetry() {
        when(accounts.findByAccountIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(account(1L, "10.00")));
        when(wallets.findByUserIdForUpdate(1L))
                .thenReturn(Optional.of(wallet(1L, "0.00")));
        when(transactions.saveAndFlush(any(Transaction.class)))
                .thenThrow(new IllegalStateException("Simulated write failure"));
        assertThrows(IllegalStateException.class,
                () -> service.addFunds(1L, new AddFundsRequest(10L, money("1.00"))));
        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
        verify(transactions, times(1)).saveAndFlush(any(Transaction.class));
        assertFalse(guardHeld);
        // Mock entities do not simulate database rollback; Oracle must verify restoration.
    }

    @Test
    void rejectsSelfPaymentBeforeRepositoriesAreUsed() {
        assertThrows(ForbiddenOperationException.class,
                () -> service.makePayment(1L, new MakePaymentRequest(1L, money("1.00"))));
        verifyNoInteractions(accounts, wallets, transactions);
    }

    @Test
    void rejectsExcessPrecisionBeforeRepositoriesAreUsed() {
        assertThrows(InvalidMoneyException.class,
                () -> service.addFunds(1L, new AddFundsRequest(10L, money("1.001"))));
        verifyNoInteractions(accounts, wallets, transactions);
    }

    @Test
    void frozenAccountCannotBeDebited() {
        Account source = account(1L, "10.00");
        ReflectionTestUtils.setField(source, "status", AccountStatus.FROZEN);
        when(accounts.findByAccountIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(source));
        assertThrows(ForbiddenOperationException.class,
                () -> service.addFunds(1L, new AddFundsRequest(10L, money("1.00"))));
        verifyNoInteractions(wallets, transactions);
    }

    @Test
    void missingOrUnownedSourceIsRejected() {
        when(accounts.findByAccountIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.addFunds(1L, new AddFundsRequest(10L, money("1.00"))));
        verifyNoInteractions(wallets, transactions);
    }

    @Test
    void accountQueryDoesNotRevealAnotherUsersBalance() {
        when(accounts.findById(10L)).thenReturn(Optional.of(account(2L, "10.00")));
        assertThrows(ResourceNotFoundException.class,
                () -> new AccountQueryServiceImpl(accounts).getBalance(1L, 10L));
    }

    @Test
    void accountCreationUsesConfiguredOpeningAmountAndCurrentUser() {
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            assertEquals(1L, account.getUserId());
            assertTrue(guardHeld);
            return account;
        });
        var accountService = new AccountCommandServiceImpl(accounts, localTransaction, 1234, 1234);
        assertEquals(money("1234.00"), accountService.addAccount(1L).balance());
        assertEquals(1, transactionManager.commits);
    }

    @Test
    void rejectsInvalidOpeningRangeAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new AccountCommandServiceImpl(accounts, localTransaction, 10, 5));
    }

    @Test
    void transactionQueryRejectsUnboundedPageSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new TransactionQueryServiceImpl(transactions)
                        .getAll(1L, PageRequest.of(0, 101)));
        verifyNoInteractions(transactions);
    }

    @Test
    void transactionHistoryIncludesDestinationWalletOwner() {
        var pageable = PageRequest.of(0, 20);
        var transaction = new Transaction(
                TransactionType.W2W, wallet(1L, "10.00"), null,
                wallet(2L, "5.00"), money("1.00"));
        when(transactions.findAllVisibleTransactions(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(transaction), pageable, 1));

        var result = new TransactionQueryServiceImpl(transactions)
                .getAll(1L, pageable);

        assertEquals(2L, result.getContent().get(0).destinationUserId());
    }

    @Test
    void guardDenialPreventsAnyLocalTransaction() {
        UserOperationGuard rejectingGuard = new UserOperationGuard() {
            @Override
            public <T> T withActiveUsers(Long current, Long recipient, Supplier<T> action) {
                throw new ForbiddenOperationException("Profile is inactive.");
            }
        };
        var guarded = new GuardedLocalTransaction(rejectingGuard, transactionManager);
        assertThrows(ForbiddenOperationException.class,
                () -> guarded.execute(1L, null, () -> fail("Must not execute")));
        assertEquals(0, transactionManager.commits);
        assertEquals(0, transactionManager.rollbacks);
    }

    @Test
    void nestedMoneyTransactionIsRejected() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class,
                    () -> localTransaction.execute(1L, null, () -> null));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private void recordTransactions() {
        when(transactions.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> {
            assertTrue(guardHeld);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            Transaction transaction = invocation.getArgument(0);
            assertNotEquals(TransactionStatus.PENDING, transaction.getStatus());
            return transaction;
        });
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static Wallet wallet(Long userId, String balance) {
        Wallet wallet = new Wallet(userId);
        ReflectionTestUtils.setField(wallet, "walletId", userId * 100);
        ReflectionTestUtils.setField(wallet, "balance", money(balance));
        return wallet;
    }

    private static Account account(Long userId, String balance) {
        Account account = new Account(userId, money(balance));
        ReflectionTestUtils.setField(account, "accountId", 10L);
        return account;
    }

    private class TrackingTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {
            assertTrue(guardHeld);
            commits++;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            assertTrue(guardHeld);
            rollbacks++;
        }
    }
}

