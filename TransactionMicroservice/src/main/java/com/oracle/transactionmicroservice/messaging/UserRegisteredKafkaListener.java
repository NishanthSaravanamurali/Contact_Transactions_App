package com.oracle.transactionmicroservice.messaging;

import com.oracle.transactionmicroservice.messaging.dto.UserRegisteredEvent;
import com.oracle.transactionmicroservice.service.abstractions.WalletCommandService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class UserRegisteredKafkaListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(UserRegisteredKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final UserRegisteredEventValidator validator;
    private final WalletCommandService walletCommandService;

    public UserRegisteredKafkaListener(
            ObjectMapper objectMapper,
            UserRegisteredEventValidator validator,
            WalletCommandService walletCommandService
    ) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.walletCommandService = walletCommandService;
    }

    @KafkaListener(
            topics = "${messaging.kafka.user-lifecycle-topic}",
            groupId = "${messaging.kafka.wallet-provisioner-group-id}")
    public void consume(ConsumerRecord<String, String> record) {
        UserRegisteredEvent event = deserialize(record.value());
        validator.validate(record.key(), event);

        LOGGER.info(
                "Provisioning wallet from user event; eventId={}, userId={}",
                event.eventId(),
                event.userId());

        // createWallet is transactional. The listener returns only after its
        // Oracle transaction has committed; only then may Kafka advance the offset.
        try {
            walletCommandService.createWallet(event.userId());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Wallet provisioning failed; eventId={}, userId={}",
                    event.eventId(),
                    event.userId());
            throw exception;
        }

        LOGGER.info(
                "Wallet provisioning completed; eventId={}, userId={}",
                event.eventId(),
                event.userId());
    }

    private UserRegisteredEvent deserialize(String value) {
        if (value == null || value.isBlank()) {
            throw new MalformedUserLifecycleEventException(
                    "Kafka record value is required.");
        }
        try {
            return objectMapper.readValue(value, UserRegisteredEvent.class);
        } catch (JacksonException exception) {
            throw new MalformedUserLifecycleEventException(
                    "Kafka record value is not a valid UserRegistered envelope.",
                    exception);
        }
    }
}
