package com.oracle.transactionmicroservice.service.implementations;

import com.oracle.transactionmicroservice.service.abstractions.UserOperationGuard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Supplier;

@Component
public class GuardedLocalTransaction {
    private final UserOperationGuard userOperationGuard;
    private final TransactionTemplate transactionTemplate;

    public GuardedLocalTransaction(UserOperationGuard userOperationGuard,
                                   PlatformTransactionManager transactionManager) {
        this.userOperationGuard = userOperationGuard;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    public <T> T execute(Long currentUserId, Long recipientUserId, Supplier<T> operation) {
        // Coordination must remain held until this transaction actually ends.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Money commands must start outside an existing transaction.");
        }
        return userOperationGuard.withActiveUsers(currentUserId, recipientUserId,
                () -> transactionTemplate.execute(status -> operation.get()));
    }
}

