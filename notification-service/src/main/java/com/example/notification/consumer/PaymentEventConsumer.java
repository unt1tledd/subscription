package com.example.notification.consumer;

import com.example.notification.dto.PaymentStatusChangedEvent;
import com.example.notification.errors.InvalidPaymentEventException;
import com.example.notification.usecase.PaymentEventHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {
    private final ObjectMapper objectMapper;
    private final PaymentEventHandler paymentEventHandler;

    public PaymentEventConsumer(
            ObjectMapper objectMapper,
            PaymentEventHandler paymentEventHandler
    ) {
        this.objectMapper = objectMapper;
        this.paymentEventHandler = paymentEventHandler;
    }

    @KafkaListener(
            topics = "${notification.kafka.payment-topic}",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) throws Exception {

        PaymentStatusChangedEvent event =
                objectMapper.readValue(
                        record.value(),
                        PaymentStatusChangedEvent.class
                );

        validateKey(record.key(), event);

        paymentEventHandler.handle(event, record.value());
    }

    private void validateKey(String key, PaymentStatusChangedEvent event) {
        if (key == null) {
            throw new InvalidPaymentEventException("Kafka message key must not be null");
        }

        if (!key.equals(event.paymentId().toString())) {
            throw new InvalidPaymentEventException("Kafka key does not match paymentId");
        }
    }
}