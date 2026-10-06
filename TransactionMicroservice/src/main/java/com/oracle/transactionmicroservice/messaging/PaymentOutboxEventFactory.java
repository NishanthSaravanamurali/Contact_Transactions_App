package com.oracle.transactionmicroservice.messaging;

import com.oracle.transactionmicroservice.entity.PaymentOutboxEvent;
import com.oracle.transactionmicroservice.entity.Transaction;
import com.oracle.transactionmicroservice.enums.*;
import com.oracle.transactionmicroservice.messaging.dto.PaymentCompletedEvent;
import com.oracle.transactionmicroservice.repository.PaymentOutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.time.*;

@Component
public class PaymentOutboxEventFactory {
    private final PaymentOutboxEventRepository repository;
    private final ObjectMapper mapper;

    public PaymentOutboxEventFactory(PaymentOutboxEventRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Transaction transaction, Long senderId, Long receiverId, String senderName) {
        if (transaction.getTransactionType() != TransactionType.W2W
                || transaction.getStatus() != TransactionStatus.COMPLETED
                || transaction.getTransactionId() == null || transaction.getCompletedAt() == null) {
            throw new IllegalArgumentException("Only persisted successful payments may create notification events.");
        }
        if (senderName == null || senderName.isBlank() || senderName.length() > 100) {
            throw new IllegalArgumentException("A valid sender display name is required.");
        }
        // Transaction.markCompleted() uses LocalDateTime.now(), i.e. the JVM zone.
        // Attach that SAME zone before serializing an offset-aware timestamp.
        var event = new PaymentCompletedEvent(transaction.getTransactionId(), senderId,
                senderName, receiverId, transaction.getAmount(), "INR",
                transaction.getCompletedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        repository.saveAndFlush(new PaymentOutboxEvent(transaction.getTransactionId(), receiverId,
                mapper.writeValueAsString(event), LocalDateTime.now(ZoneOffset.UTC)));
    }
}
