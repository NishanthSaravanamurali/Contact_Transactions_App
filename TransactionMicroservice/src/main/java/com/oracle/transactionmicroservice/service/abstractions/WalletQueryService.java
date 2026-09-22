package com.oracle.transactionmicroservice.service.abstractions;

import com.oracle.transactionmicroservice.dto.response.WalletResponse;

public interface WalletQueryService {
    WalletResponse getBalance(Long currentUserId);
}

