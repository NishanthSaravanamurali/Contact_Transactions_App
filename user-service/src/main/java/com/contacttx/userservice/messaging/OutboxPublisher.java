package com.contacttx.userservice.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

    private final OutboxPublicationService publicationService;

    public OutboxPublisher(OutboxPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @Scheduled(
            initialDelayString = "${outbox.publisher.initial-delay-ms}",
            fixedDelayString = "${outbox.publisher.fixed-delay-ms}")
    public void publishPendingEvents() {
        publicationService.publishPendingBatch();
    }
}
