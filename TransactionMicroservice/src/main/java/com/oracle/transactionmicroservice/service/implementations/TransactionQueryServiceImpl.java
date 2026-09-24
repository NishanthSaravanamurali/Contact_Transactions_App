package com.oracle.transactionmicroservice.service.implementations;

import com.oracle.transactionmicroservice.dto.response.TransactionResponse;
import com.oracle.transactionmicroservice.entity.Transaction;
import com.oracle.transactionmicroservice.exception.ResourceNotFoundException;
import com.oracle.transactionmicroservice.repository.TransactionRepository;
import com.oracle.transactionmicroservice.service.abstractions.TransactionQueryService;
import com.oracle.transactionmicroservice.service.abstractions.UserDisplayNameResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class TransactionQueryServiceImpl implements TransactionQueryService {
    private final TransactionRepository transactionRepository;
    private final UserDisplayNameResolver displayNameResolver;

    public TransactionQueryServiceImpl(
            TransactionRepository transactionRepository,
            UserDisplayNameResolver displayNameResolver
    ) {
        this.transactionRepository = transactionRepository;
        this.displayNameResolver = displayNameResolver;
    }

    @Override
    public TransactionResponse getById(Long currentUserId, Long transactionId) {
        ServiceSupport.requireCurrentUser(currentUserId);
        ServiceSupport.requireResourceId(transactionId, "Transaction");
        Transaction transaction = transactionRepository
                .findVisibleTransactionById(transactionId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));
        var displayNames = displayNameResolver.resolve(userIds(transaction));
        return ServiceSupport.response(transaction, displayNames);
    }

    @Override
    public Page<TransactionResponse> getAll(Long currentUserId, Pageable pageable) {
        ServiceSupport.requireCurrentUser(currentUserId);
        if (pageable == null || pageable.isUnpaged() || pageable.getPageSize() > 100
                || pageable.getSort().isSorted()) {
            throw new IllegalArgumentException(
                    "Use unsorted pagination with a page size of 1 to 100; history is newest first.");
        }
        Page<Transaction> transactions =
                transactionRepository.findAllVisibleTransactions(currentUserId, pageable);
        Set<Long> userIds = new LinkedHashSet<>();
        transactions.forEach(transaction -> userIds.addAll(userIds(transaction)));
        var displayNames = displayNameResolver.resolve(userIds);
        return transactions.map(transaction -> ServiceSupport.response(transaction, displayNames));
    }

    private Set<Long> userIds(Transaction transaction) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (transaction.getSourceWallet() != null) {
            userIds.add(transaction.getSourceWallet().getUserId());
        }
        userIds.add(transaction.getDestinationWallet().getUserId());
        return userIds;
    }
}

