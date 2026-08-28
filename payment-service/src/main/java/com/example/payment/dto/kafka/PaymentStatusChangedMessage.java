package com.example.payment.dto.kafka;

import com.example.payment.entity.PaymentStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PaymentStatusChangedMessage(
        UUID eventId,
        UUID paymentId,
        PaymentStatus paymentStatus,
        Map<String, Object> payload,
        Instant occurredAt
) {

    public static PaymentStatusChangedMessage from(
            ClaimedOutboxEvent event
    ) {
        return new PaymentStatusChangedMessage(
                event.eventId(),
                event.paymentId(),
                event.paymentStatus(),
                event.payload(),
                event.createdAt()
        );
    }
}
