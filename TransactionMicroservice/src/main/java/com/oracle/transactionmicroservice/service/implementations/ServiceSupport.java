package com.oracle.transactionmicroservice.service.implementations;

import com.oracle.transactionmicroservice.dto.response.AccountResponse;
import com.oracle.transactionmicroservice.dto.response.TransactionResponse;
import com.oracle.transactionmicroservice.dto.response.WalletResponse;
import com.oracle.transactionmicroservice.entity.Account;
import com.oracle.transactionmicroservice.entity.Transaction;
import com.oracle.transactionmicroservice.entity.Wallet;
import com.oracle.transactionmicroservice.exception.ForbiddenOperationException;
import com.oracle.transactionmicroservice.exception.ResourceNotFoundException;

final class ServiceSupport {
    private ServiceSupport() {}

    static void requireCurrentUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ForbiddenOperationException("An authenticated user is required.");
        }
    }

    static void requireResourceId(Long id, String resource) {
        if (id == null || id <= 0) {
            throw new ResourceNotFoundException(resource + " not found.");
        }
    }

    static AccountResponse response(Account account) {
        return new AccountResponse(account.getAccountId(), account.getBalance(),
                account.getStatus(), account.getCreatedAt(), account.getUpdatedAt());
    }

    static WalletResponse response(Wallet wallet) {
        return new WalletResponse(wallet.getWalletId(), wallet.getBalance(),
                wallet.getCreatedAt(), wallet.getUpdatedAt());
    }

    static TransactionResponse response(Transaction transaction) {
        return new TransactionResponse(
                transaction.getTransactionId(), transaction.getTransactionType(),
                transaction.getSourceWallet() == null ? null
                        : transaction.getSourceWallet().getWalletId(),
                transaction.getSourceAccount() == null ? null
                        : transaction.getSourceAccount().getAccountId(),
                transaction.getDestinationWallet().getWalletId(),
                transaction.getAmount(), transaction.getStatus(),
                transaction.getCreatedAt(), transaction.getCompletedAt());
    }
}

