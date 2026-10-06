package com.oracle.transactionmicroservice.controllers;

import com.oracle.transactionmicroservice.dto.request.CreateWalletRequest;
import com.oracle.transactionmicroservice.dto.response.WalletResponse;
import com.oracle.transactionmicroservice.service.abstractions.WalletCommandService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/v1/wallets")
public class InternalWalletController {

    private final WalletCommandService walletCommandService;
    private final String expectedUserServiceToken;

    public InternalWalletController(
            WalletCommandService walletCommandService,
            @Value("${integration.internal.user-service-token}")
            String expectedUserServiceToken
    ) {
        if (expectedUserServiceToken == null || expectedUserServiceToken.isBlank()) {
            throw new IllegalStateException(
                    "User Service internal token is not configured."
            );
        }

        this.walletCommandService = walletCommandService;
        this.expectedUserServiceToken = expectedUserServiceToken;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(
            @RequestHeader(
                    name = "X-Internal-Service-Token",
                    required = false
            ) String internalServiceToken,
            @Valid @RequestBody CreateWalletRequest request
    ) {
        verifyUserServiceToken(internalServiceToken);

        return walletCommandService.createWallet(request.userId());
    }

    private void verifyUserServiceToken(String receivedToken) {
        if (receivedToken == null || !MessageDigest.isEqual(
                expectedUserServiceToken.getBytes(StandardCharsets.UTF_8),
                receivedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid internal service token."
            );
        }
    }
}