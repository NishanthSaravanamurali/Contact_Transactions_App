package com.oracle.transactionmicroservice.service.abstractions;

import com.oracle.transactionmicroservice.dto.response.AccountResponse;

public interface AccountCommandService {
    AccountResponse addAccount(Long currentUserId);
}

