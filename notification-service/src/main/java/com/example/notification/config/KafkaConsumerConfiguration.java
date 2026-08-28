package com.example.notification.config;

import com.example.notification.errors.InvalidPaymentEventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfiguration {

    @Bean
    public DefaultErrorHandler paymentEventErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${notification.kafka.dlt-suffix}")
            String dltSuffix,
            @Value("${notification.kafka.retry-delay-ms}")
            long retryDelay,
            @Value("${notification.kafka.retry-attempts}")
            long retryAttempts
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition(
                                        record.topic() + dltSuffix,
                                        record.partition()
                                )
                );

        FixedBackOff backOff = new FixedBackOff(
                retryDelay,
                retryAttempts
        );

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        backOff
                );

        errorHandler.addNotRetryableExceptions(
                JsonProcessingException.class,
                InvalidPaymentEventException.class
        );

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    paymentKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();


        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        return factory;
    }

    @Bean
    public NewTopic paymentEventsDltTopic(
            @Value("${notification.kafka.payment-topic}")
            String paymentTopic,
            @Value("${notification.kafka.dlt-suffix}")
            String dltSuffix,
            @Value("${notification.kafka.partitions}")
            int partitions
    ) {
        return TopicBuilder
                .name(paymentTopic + dltSuffix)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}