package com.contacttx.userservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WalletProvisioningListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WalletProvisioningListener.class);

    private final TransactionWalletClient transactionWalletClient;

    public WalletProvisioningListener(TransactionWalletClient transactionWalletClient) {
        this.transactionWalletClient = transactionWalletClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void provisionWallet(UserRegisteredEvent event) {
        try {
            transactionWalletClient.createWallet(event.getUserId());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Wallet creation request failed after registration committed; userId={}",
                    event.getUserId());
        }
    }
}
