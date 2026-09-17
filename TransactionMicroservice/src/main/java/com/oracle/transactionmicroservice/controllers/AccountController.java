package com.oracle.transactionmicroservice.controllers;

import com.oracle.transactionmicroservice.dto.response.AccountResponse;
import com.oracle.transactionmicroservice.service.abstractions.AccountCommandService;
import com.oracle.transactionmicroservice.service.abstractions.AccountQueryService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountCommandService accountCommandService;
    private final AccountQueryService accountQueryService;
    private final UserIdResolver currentUserIdResolver;

    public AccountController(
            AccountCommandService accountCommandService,
            AccountQueryService accountQueryService,
            UserIdResolver currentUserIdResolver
    ) {
        this.accountCommandService = accountCommandService;
        this.accountQueryService = accountQueryService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @PostMapping("/addAccount")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse addAccount(@AuthenticationPrincipal Jwt jwt) {
        Long currentUserId = currentUserIdResolver.resolve(jwt);
        return accountCommandService.addAccount(currentUserId);
    }

    @GetMapping("/getAccounts")
    public List<AccountResponse> getAccounts(@AuthenticationPrincipal Jwt jwt) {
        Long currentUserId = currentUserIdResolver.resolve(jwt);
        return accountQueryService.getAccounts(currentUserId);
    }

    @GetMapping("/getBalance")
    public AccountResponse getBalance(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @Positive Long accountId
    ) {
        Long currentUserId = currentUserIdResolver.resolve(jwt);
        return accountQueryService.getBalance(currentUserId, accountId);
    }
}