package com.oracle.transactionmicroservice.service.implementations;

import com.oracle.transactionmicroservice.dto.response.TransactionResponse;
import com.oracle.transactionmicroservice.exception.ResourceNotFoundException;
import com.oracle.transactionmicroservice.repository.TransactionRepository;
import com.oracle.transactionmicroservice.service.abstractions.TransactionQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionQueryServiceImpl implements TransactionQueryService {
    private final TransactionRepository transactionRepository;

    public TransactionQueryServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public TransactionResponse getById(Long currentUserId, Long transactionId) {
        ServiceSupport.requireCurrentUser(currentUserId);
        ServiceSupport.requireResourceId(transactionId, "Transaction");
        return transactionRepository.findVisibleTransactionById(transactionId, currentUserId)
                .map(ServiceSupport::response)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));
    }

    @Override
    public Page<TransactionResponse> getAll(Long currentUserId, Pageable pageable) {
        ServiceSupport.requireCurrentUser(currentUserId);
        if (pageable == null || pageable.isUnpaged() || pageable.getPageSize() > 100
                || pageable.getSort().isSorted()) {
            throw new IllegalArgumentException(
                    "Use unsorted pagination with a page size of 1 to 100; history is newest first.");
        }
        return transactionRepository.findAllVisibleTransactions(currentUserId, pageable)
                .map(ServiceSupport::response);
    }
}

