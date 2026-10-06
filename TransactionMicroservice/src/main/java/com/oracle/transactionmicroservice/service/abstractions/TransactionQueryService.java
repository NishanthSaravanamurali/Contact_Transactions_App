package com.oracle.transactionmicroservice.service.abstractions;

import com.oracle.transactionmicroservice.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionQueryService {
    TransactionResponse getById(Long currentUserId, Long transactionId);
    Page<TransactionResponse> getAll(Long currentUserId, Pageable pageable);
}

