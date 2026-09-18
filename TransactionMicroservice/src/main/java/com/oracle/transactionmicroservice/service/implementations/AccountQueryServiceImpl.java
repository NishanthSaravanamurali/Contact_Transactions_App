package com.oracle.transactionmicroservice.service.implementations;

import com.oracle.transactionmicroservice.dto.response.AccountResponse;
import com.oracle.transactionmicroservice.exception.ResourceNotFoundException;
import com.oracle.transactionmicroservice.repository.AccountRepository;
import com.oracle.transactionmicroservice.service.abstractions.AccountQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AccountQueryServiceImpl implements AccountQueryService {
    private final AccountRepository accountRepository;

    public AccountQueryServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public List<AccountResponse> getAccounts(Long currentUserId) {
        ServiceSupport.requireCurrentUser(currentUserId);
        return accountRepository.findAllByUserId(currentUserId).stream()
                .map(ServiceSupport::response).toList();
    }

    @Override
    public AccountResponse getBalance(Long currentUserId, Long accountId) {
        ServiceSupport.requireCurrentUser(currentUserId);
        ServiceSupport.requireResourceId(accountId, "Account");
        return accountRepository.findById(accountId)
                .filter(account -> currentUserId.equals(account.getUserId()))
                .map(ServiceSupport::response)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
    }
}

