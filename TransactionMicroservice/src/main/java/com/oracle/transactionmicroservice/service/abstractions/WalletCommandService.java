package com.oracle.transactionmicroservice.service.abstractions;

import com.oracle.transactionmicroservice.dto.request.AddFundsRequest;
import com.oracle.transactionmicroservice.dto.request.MakePaymentRequest;
import com.oracle.transactionmicroservice.dto.response.TransactionResponse;

public interface WalletCommandService {
    TransactionResponse addFunds(Long currentUserId, AddFundsRequest request);
    TransactionResponse makePayment(Long currentUserId, MakePaymentRequest request);
}

