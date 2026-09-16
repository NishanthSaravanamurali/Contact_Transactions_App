package com.oracle.transactionmicroservice.service.abstractions;

import com.oracle.transactionmicroservice.dto.response.AccountResponse;
import java.util.List;

public interface AccountQueryService {
    List<AccountResponse> getAccounts(Long currentUserId);
    AccountResponse getBalance(Long currentUserId, Long accountId);
}

