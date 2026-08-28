package com.example.payment.dto.kafka;

import com.example.payment.entity.OutboxEvent;
import com.example.payment.entity.PaymentStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ClaimedOutboxEvent(
        UUID eventId,
        UUID paymentId,
        PaymentStatus paymentStatus,
        Map<String, Object> payload,
        Instant createdAt
) {

    public static ClaimedOutboxEvent from(
            OutboxEvent event
    ) {
        return new ClaimedOutboxEvent(
                event.getId(),
                event.getPaymentId(),
                event.getPaymentStatus(),
                event.getPayload(),
                event.getCreatedAt()
        );
    }
}