package com.contacttx.userservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "OUTBOX_EVENT")
public class OutboxEvent {

    @Id
    @Column(name = "EVENT_ID", length = 36, updatable = false)
    private String eventId;

    @Column(name = "EVENT_TYPE", length = 100, updatable = false)
    private String eventType;

    @Column(name = "AGGREGATE_ID", updatable = false)
    private Long aggregateId;

    @Lob
    @Column(name = "PAYLOAD", updatable = false)
    private String payload;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "PUBLISHED_AT")
    private LocalDateTime publishedAt;

    @Column(name = "PUBLISH_ATTEMPTS")
    private int publishAttempts;

    @Column(name = "LAST_ERROR", length = 1000)
    private String lastError;

    protected OutboxEvent() {
        // Required by JPA.
    }

    public OutboxEvent(
            String eventId,
            String eventType,
            Long aggregateId,
            String payload,
            LocalDateTime createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = createdAt;
        this.publishAttempts = 0;
    }

    public void markPublished(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void markPublicationFailed(String errorMessage) {
        this.publishAttempts++;
        this.lastError = errorMessage;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public int getPublishAttempts() {
        return publishAttempts;
    }

    public String getLastError() {
        return lastError;
    }
}
