package com.example.subscription.dto.subscription;

import com.example.subscription.entity.Subscription;
import com.example.subscription.entity.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        long userId,
        String planCode,
        SubscriptionStatus status,
        Instant startedAt,
        Instant expiresAt,
        boolean autoRenew,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static SubscriptionResponse from(Subscription sub) {
        return new SubscriptionResponse(
                sub.getId(),
                sub.getUserId(),
                sub.getPlan().getCode(),
                sub.getStatus(),
                sub.getStartedAt(),
                sub.getExpiresAt(),
                sub.isAutoRenew(),
                sub.getCancelledAt(),
                sub.getCreatedAt(),
                sub.getUpdatedAt()
        );
    }
}
