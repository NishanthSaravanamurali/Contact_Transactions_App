package com.oracle.transactionmicroservice.controllers;

import com.oracle.transactionmicroservice.dto.response.TransactionResponse;
import com.oracle.transactionmicroservice.service.abstractions.TransactionQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/money/transactions")
@Validated
public class TransactionController {

    private final TransactionQueryService transactionQueryService;
    private final UserIdResolver currentUserIdResolver;

    public TransactionController(
            TransactionQueryService transactionQueryService,
            UserIdResolver currentUserIdResolver
    ) {
        this.transactionQueryService = transactionQueryService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @GetMapping("/getAll")
    public Page<TransactionResponse> getAll(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Long currentUserId = currentUserIdResolver.resolve(jwt);
        Pageable pageable = PageRequest.of(page, size);

        return transactionQueryService.getAll(currentUserId, pageable);
    }

    @GetMapping("/get/{transactionId}")
    public TransactionResponse getById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(1) Long transactionId
    ) {
        Long currentUserId = currentUserIdResolver.resolve(jwt);
        return transactionQueryService.getById(currentUserId, transactionId);
    }
}
