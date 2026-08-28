package com.example.payment.workers;

import com.example.payment.dto.kafka.ClaimedOutboxEvent;
import com.example.payment.dto.kafka.PaymentStatusChangedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class PaymentEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public PaymentEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${payment.outbox.topic}")
            String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(ClaimedOutboxEvent event
    ) throws JsonProcessingException,
            ExecutionException,
            InterruptedException,
            TimeoutException
    {

        PaymentStatusChangedMessage message = PaymentStatusChangedMessage.from(event);

        String json = objectMapper.writeValueAsString(message);

        kafkaTemplate.send(topic, event.paymentId().toString(), json)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}