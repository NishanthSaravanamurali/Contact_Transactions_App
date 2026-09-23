package com.oracle.transactionmicroservice.config;

import com.oracle.transactionmicroservice.messaging.MalformedUserLifecycleEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {

    @Bean
    public NewTopic userLifecycleDeadLetterTopic(
            @Value("${messaging.kafka.user-lifecycle-dlt-topic}") String dltTopic
    ) {
        return TopicBuilder.name(dltTopic).build();
    }

    @Bean
    public CommonErrorHandler userLifecycleErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${messaging.kafka.user-lifecycle-dlt-topic}") String dltTopic,
            @Value("${messaging.kafka.retry.interval-ms}") long retryIntervalMs,
            @Value("${messaging.kafka.retry.max-retries}") long maxRetries
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition(dltTopic, record.partition()));
        recoverer.setFailIfSendResultIsError(true);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryIntervalMs, maxRetries));

        // Retrying cannot make a malformed envelope valid. It is sent directly
        // to the DLT; database and other runtime failures use bounded retries.
        errorHandler.addNotRetryableExceptions(
                MalformedUserLifecycleEventException.class);
        return errorHandler;
    }
}
