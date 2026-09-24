package com.oracle.transactionmicroservice.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_outbox")
public class PaymentOutboxEvent implements Persistable<Long> {
    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private Long transactionId;
    @Column(name = "receiver_user_id", nullable = false, updatable = false)
    private Long receiverUserId;
    @Lob
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;
    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Transient
    private boolean newRecord = true;

    protected PaymentOutboxEvent() {}

    public PaymentOutboxEvent(Long transactionId, Long receiverUserId, String payload, LocalDateTime now) {
        this.transactionId = transactionId;
        this.receiverUserId = receiverUserId;
        this.payload = payload;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    @PostLoad
    @PostPersist
    private void persisted() { newRecord = false; }

    @Override public Long getId() { return transactionId; }
    @Override public boolean isNew() { return newRecord; }
    public Long getTransactionId() { return transactionId; }
    public Long getReceiverUserId() { return receiverUserId; }
    public String getPayload() { return payload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public int getPublishAttempts() { return publishAttempts; }
    public String getLastError() { return lastError; }

    public void markPublished(LocalDateTime now) {
        publishAttempts++;
        publishedAt = now;
        lastError = null;
    }

    public void markFailed(LocalDateTime now, String error) {
        publishAttempts = Math.min(Integer.MAX_VALUE - 1, publishAttempts) + 1;
        // Retry indefinitely with a bounded delay; never discard an unpublished event.
        long delaySeconds = Math.min(300L, 5L << Math.min(publishAttempts - 1, 6));
        nextAttemptAt = now.plusSeconds(delaySeconds);
        lastError = error == null ? "Kafka publication failed"
                : error.substring(0, Math.min(error.length(), 1000));
    }
}
