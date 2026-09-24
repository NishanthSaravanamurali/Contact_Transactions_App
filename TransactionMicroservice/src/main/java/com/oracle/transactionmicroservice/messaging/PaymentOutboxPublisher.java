package com.oracle.transactionmicroservice.messaging;

import com.oracle.transactionmicroservice.repository.PaymentOutboxEventRepository;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
@ConditionalOnProperty(name = "payment.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentOutboxPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentOutboxPublisher.class);
    private final PaymentOutboxEventRepository repository;
    private final PaymentOutboxPublicationService publication;
    private final int batchSize;

    public PaymentOutboxPublisher(PaymentOutboxEventRepository repository,
            PaymentOutboxPublicationService publication,
            @Value("${payment.outbox.batch-size}") int batchSize) {
        this.repository = repository;
        this.publication = publication;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms}")
    public void publishPending() {
        for (Long id : repository.findPendingIds(LocalDateTime.now(ZoneOffset.UTC),
                PageRequest.of(0, batchSize))) {
            if (Thread.currentThread().isInterrupted()) break;
            try { publication.publishOne(id); }
            catch (RuntimeException exception) {
                // A failed DB commit leaves the event pending. Don't log private contact labels/payloads.
                LOG.warn("Payment outbox transaction {} remains pending ({})",
                        id, exception.getClass().getSimpleName());
            }
        }
    }

    @Scheduled(fixedDelayString = "${payment.outbox.cleanup-interval-ms}")
    public void cleanupPublished() {
        // Drain a bounded number of batches per run without one large delete transaction.
        for (int batch = 0; batch < 20 && !Thread.currentThread().isInterrupted(); batch++) {
            if (publication.cleanupPublished() < batchSize) break;
        }
    }
}
