package com.example.notification.dto;

import com.example.notification.entity.PaymentEventStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentStatusChangedEvent(
        UUID eventId,
        UUID paymentId,
        PaymentEventStatus paymentStatus,
        PaymentEventPayload payload,
        Instant occurredAt
) {

    public PaymentStatusChangedEvent {
        Objects.requireNonNull(
                eventId,
                "eventId must not be null"
        );

        Objects.requireNonNull(
                paymentId,
                "paymentId must not be null"
        );

        Objects.requireNonNull(
                paymentStatus,
                "paymentStatus must not be null"
        );

        Objects.requireNonNull(
                payload,
                "payload must not be null"
        );

        Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
        );

        if (payload.userId() <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }

        if (payload.amount() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }
}