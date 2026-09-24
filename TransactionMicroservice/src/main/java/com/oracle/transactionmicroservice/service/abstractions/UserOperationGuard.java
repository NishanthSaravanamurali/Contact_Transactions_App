package com.oracle.transactionmicroservice.service.abstractions;

import java.util.function.Supplier;
import java.util.function.Function;

/**
 * Required integration boundary; implement through the agreed Identity/Contact
 * lifecycle protocol, never by directly reading another service's tables.
 *
 * Validate the current profile and optional recipient are active. For payments,
 * revalidate recipient eligibility/current contact mapping through Contact.
 * Prevent deletion/locking/remapping from racing the action until its local
 * transaction has committed or rolled back. A one-time HTTP lookup is not enough.
 *
 * Invoke action synchronously exactly once; never retry it after an uncertain
 * result. Release coordination in finally. A release error after commit must be
 * reported as an uncertain outcome, not as proof that the payment failed.
 *
 * No permissive default is supplied. Without an integration bean, command
 * services intentionally cannot be wired.
 */
public interface UserOperationGuard {
    /**
     * Pass the receiver-specific sender label to the payment, before starting its
     * local transaction. Implementations must not invent a name on lookup failure.
     */
    default <T> T withPaymentUsers(Long senderUserId, Long receiverUserId, Function<String, T> action) {
        throw new IllegalStateException("Payment display-name resolution is not configured.");
    }

    <T> T withActiveUsers(Long currentUserId, Long recipientUserId, Supplier<T> action);
}

