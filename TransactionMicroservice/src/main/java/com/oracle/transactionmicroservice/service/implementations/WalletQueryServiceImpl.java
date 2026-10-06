package com.oracle.transactionmicroservice.service.implementations;

import com.oracle.transactionmicroservice.dto.response.WalletResponse;
import com.oracle.transactionmicroservice.exception.ResourceNotFoundException;
import com.oracle.transactionmicroservice.repository.WalletRepository;
import com.oracle.transactionmicroservice.service.abstractions.WalletQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WalletQueryServiceImpl implements WalletQueryService {
    private final WalletRepository walletRepository;

    public WalletQueryServiceImpl(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Override
    public WalletResponse getBalance(Long currentUserId) {
        ServiceSupport.requireCurrentUser(currentUserId);
        return walletRepository.findByUserId(currentUserId)
                .map(ServiceSupport::response)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found."));
    }
}

