package com.oracle.transactionmicroservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler userLifecycleErrorHandler(
            @Value("${messaging.kafka.retry.interval-ms}") long retryIntervalMs) {
        // First attempt plus two retries, then log and skip. Do not log event payloads.
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) ->
                LOGGER.error("Skipping failed Kafka event after 3 attempts; topic={}, partition={}, offset={}, failureType={}. Manual recovery required.",
                        record.topic(), record.partition(), record.offset(), exception.getClass().getSimpleName()),
                new FixedBackOff(retryIntervalMs, 2L));
        // Apply the same three-attempt policy to malformed and validation failures.
        handler.setClassifications(Map.of(), true);
        handler.setResetStateOnExceptionChange(false);
        return handler;
    }
}
