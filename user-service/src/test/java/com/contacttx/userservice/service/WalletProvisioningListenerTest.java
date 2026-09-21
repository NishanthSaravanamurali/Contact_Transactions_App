package com.contacttx.userservice.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WalletProvisioningListenerTest {

    @Test
    void requestsWalletCreationForTheCommittedUser() {
        TransactionWalletClient client = mock(TransactionWalletClient.class);
        WalletProvisioningListener listener = new WalletProvisioningListener(client);

        listener.provisionWallet(new UserRegisteredEvent(42L));

        verify(client).createWallet(42L);
    }

    @Test
    void doesNotFailTheCompletedRegistrationWhenTheRemoteCallFails() {
        TransactionWalletClient client = mock(TransactionWalletClient.class);
        doThrow(new IllegalStateException("Transaction Service unavailable"))
                .when(client).createWallet(42L);
        WalletProvisioningListener listener = new WalletProvisioningListener(client);

        assertDoesNotThrow(
                () -> listener.provisionWallet(new UserRegisteredEvent(42L)));
    }
}
