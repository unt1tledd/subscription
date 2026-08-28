package com.example.notification.consumer;

import com.example.notification.dto.PaymentEventPayload;
import com.example.notification.dto.PaymentStatusChangedEvent;
import com.example.notification.entity.PaymentEventStatus;
import com.example.notification.errors.InvalidPaymentEventException;
import com.example.notification.usecase.PaymentEventHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    private static final String TOPIC = "payment-status-changed";

    @Mock
    private PaymentEventHandler paymentEventHandler;

    private ObjectMapper objectMapper;
    private PaymentEventConsumer consumer;

    private PaymentStatusChangedEvent event;
    private String rawMessage;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        consumer = new PaymentEventConsumer(
                objectMapper,
                paymentEventHandler
        );

        event = createEvent();

        rawMessage = objectMapper.writeValueAsString(event);
    }

    @Test
    void parsesValidMessageAndPassesItToHandler() throws Exception {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        TOPIC,
                        0,
                        0,
                        event.paymentId().toString(),
                        rawMessage
                );

        consumer.consume(record);

        verify(paymentEventHandler).handle(
                event,
                rawMessage
        );
    }

    @Test
    void rejectsMessageWithoutKafkaKey() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        TOPIC,
                        0,
                        0,
                        null,
                        rawMessage
                );

        assertThatThrownBy(() -> consumer.consume(record))
                .isInstanceOf(InvalidPaymentEventException.class)
                .hasMessage("Kafka message key must not be null");

        verifyNoInteractions(paymentEventHandler);
    }

    @Test
    void rejectsMessageWithDifferentPaymentIdInKey() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        TOPIC,
                        0,
                        0,
                        UUID.randomUUID().toString(),
                        rawMessage
                );

        assertThatThrownBy(() -> consumer.consume(record))
                .isInstanceOf(InvalidPaymentEventException.class)
                .hasMessage("Kafka key does not match paymentId");

        verifyNoInteractions(paymentEventHandler);
    }

    @Test
    void rejectsInvalidJson() {
        String invalidMessage = "{invalid-json";

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        TOPIC,
                        0,
                        0,
                        UUID.randomUUID().toString(),
                        invalidMessage
                );

        assertThatThrownBy(() -> consumer.consume(record)).
                isInstanceOf(JsonProcessingException.class);

        verifyNoInteractions(paymentEventHandler);
    }

    private PaymentStatusChangedEvent createEvent() {
        return new PaymentStatusChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentEventStatus.SUCCEEDED,
                new PaymentEventPayload(
                        42,
                        19_900,
                        "RUB",
                        null,
                        0
                ),
                Instant.now()
        );
    }
}