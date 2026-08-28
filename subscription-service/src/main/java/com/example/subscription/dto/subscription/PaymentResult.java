package com.example.subscription.dto.subscription;

import java.time.Instant;

public record PaymentResult(
        String paymentId,
        Status status,
        long amount,
        String currency,
        Instant createdAt
) {
    public enum Status {
        PENDING,
        SUCCEEDED,
        FAILED
    }
}