package com.oracle.transactionmicroservice.service.implementations;

import com.oracle.transactionmicroservice.dto.response.AccountResponse;
import com.oracle.transactionmicroservice.entity.Account;
import com.oracle.transactionmicroservice.repository.AccountRepository;
import com.oracle.transactionmicroservice.service.abstractions.AccountCommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountCommandServiceImpl implements AccountCommandService {
    private final AccountRepository accountRepository;
    private final GuardedLocalTransaction localTransaction;
    private final long openingMinimum;
    private final long openingMaximum;

    public AccountCommandServiceImpl(
            AccountRepository accountRepository,
            GuardedLocalTransaction localTransaction,
            @Value("${simulator.account.opening-min:1000}") long openingMinimum,
            @Value("${simulator.account.opening-max:10000}") long openingMaximum) {
        if (openingMinimum <= 0 || openingMaximum < openingMinimum
                || openingMaximum > 999_999_999_999_999_999L) {
            throw new IllegalArgumentException("Invalid simulated opening balance range.");
        }
        this.accountRepository = accountRepository;
        this.localTransaction = localTransaction;
        this.openingMinimum = openingMinimum;
        this.openingMaximum = openingMaximum;
    }

    @Override
    public AccountResponse addAccount(Long currentUserId) {
        ServiceSupport.requireCurrentUser(currentUserId);
        return localTransaction.execute(currentUserId, null, () -> {
            long openingAmount = ThreadLocalRandom.current()
                    .nextLong(openingMinimum, openingMaximum + 1);
            Account account = new Account(currentUserId,
                    BigDecimal.valueOf(openingAmount).setScale(2));
            return ServiceSupport.response(accountRepository.saveAndFlush(account));
        });
    }
}

