package com.oracle.transactionmicroservice.messaging;

import com.oracle.transactionmicroservice.repository.PaymentOutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentOutboxPublicationService {
    private final PaymentOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final long sendTimeoutMs;
    private final long retentionHours;
    private final int batchSize;

    public PaymentOutboxPublicationService(PaymentOutboxEventRepository repository,
            KafkaTemplate<String, String> kafka,
            @Value("${messaging.kafka.payment-topic}") String topic,
            @Value("${payment.outbox.send-timeout-ms}") long sendTimeoutMs,
            @Value("${payment.outbox.retention-hours}") long retentionHours,
            @Value("${payment.outbox.batch-size}") int batchSize) {
        if (topic == null || topic.isBlank() || sendTimeoutMs <= 0 || retentionHours < 1
                || batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Invalid payment outbox settings.");
        }
        this.repository = repository;
        this.kafka = kafka;
        this.topic = topic;
        this.sendTimeoutMs = sendTimeoutMs;
        this.retentionHours = retentionHours;
        this.batchSize = batchSize;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(Long transactionId) {
        var event = repository.findForPublishing(transactionId).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // Recheck after locking: another publisher may already have sent this event.
        if (event == null || event.getPublishedAt() != null || event.getNextAttemptAt().isAfter(now)) return;
        try {
            kafka.send(topic, event.getReceiverUserId().toString(), event.getPayload())
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            event.markPublished(LocalDateTime.now(ZoneOffset.UTC));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            event.markFailed(LocalDateTime.now(ZoneOffset.UTC), "Publisher interrupted");
        } catch (Exception exception) {
            // An uncertain send is retried; the notification consumer deduplicates by transactionId.
            event.markFailed(LocalDateTime.now(ZoneOffset.UTC),
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanupPublished() {
        var ids = repository.findExpiredPublishedIds(
                LocalDateTime.now(ZoneOffset.UTC).minusHours(retentionHours), PageRequest.of(0, batchSize));
        if (!ids.isEmpty()) repository.deleteAllByIdInBatch(ids);
        return ids.size();
    }
}
