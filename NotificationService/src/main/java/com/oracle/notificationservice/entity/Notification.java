package com.oracle.notificationservice.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "notifications", uniqueConstraints =
        @UniqueConstraint(name = "uq_notifications_transaction", columnNames = "transaction_id"))
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;
    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;
    @Column(name = "receiver_user_id", nullable = false)
    private Long receiverUserId;
    @Column(name = "message", nullable = false, length = 500)
    private String message;
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6) WITH TIME ZONE")
    private OffsetDateTime createdAt;
    @Column(name = "read_at", columnDefinition = "TIMESTAMP(6) WITH TIME ZONE")
    private OffsetDateTime readAt;

    protected Notification() {}

    public Notification(Long transactionId, Long senderUserId, Long receiverUserId, String message) {
        this.transactionId = transactionId;
        this.senderUserId = senderUserId;
        this.receiverUserId = receiverUserId;
        this.message = message;
    }

    @PrePersist
    private void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getNotificationId() { return notificationId; }
    public Long getTransactionId() { return transactionId; }
    public Long getSenderUserId() { return senderUserId; }
    public Long getReceiverUserId() { return receiverUserId; }
    public String getMessage() { return message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getReadAt() { return readAt; }
}
