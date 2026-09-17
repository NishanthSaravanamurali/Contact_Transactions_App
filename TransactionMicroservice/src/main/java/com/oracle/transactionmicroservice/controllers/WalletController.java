package com.oracle.transactionmicroservice.controllers;

import com.oracle.transactionmicroservice.dto.request.AddFundsRequest;
import com.oracle.transactionmicroservice.dto.request.MakePaymentRequest;
import com.oracle.transactionmicroservice.dto.response.TransactionResponse;
import com.oracle.transactionmicroservice.dto.response.WalletResponse;
import com.oracle.transactionmicroservice.service.abstractions.WalletCommandService;
import com.oracle.transactionmicroservice.service.abstractions.WalletQueryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final WalletCommandService walletCommandService;
    private final WalletQueryService walletQueryService;
    private final UserIdResolver currentUserIdResolver;

    public WalletController(
            WalletCommandService walletCommandService,
            WalletQueryService walletQueryService,
            UserIdResolver currentUserIdResolver
    ) {
        this.walletCommandService = walletCommandService;
        this.walletQueryService = walletQueryService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @PostMapping("/addFunds")
    public TransactionResponse addFunds(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddFundsRequest request
    ) {
        Long currentUserId = currentUserIdResolver.resolve(jwt);
        return walletCommandService.addFunds(currentUserId, request);
    }

    @PostMapping("/makePayment")
    public TransactionResponse makePayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MakePaymentRequest request
    ) {
        Long currentUserId = currentUserIdResolver.resolve(jwt);
        return walletCommandService.makePayment(currentUserId, request);
    }

    @GetMapping("/getBalance")
    public WalletResponse getBalance(@AuthenticationPrincipal Jwt jwt) {
        Long currentUserId = currentUserIdResolver.resolve(jwt);
        return walletQueryService.getBalance(currentUserId);
    }
}