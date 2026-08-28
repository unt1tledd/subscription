package com.example.subscription.dto.subscription;

import com.example.subscription.entity.Subscription;
import com.example.subscription.entity.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record CheckoutSubscriptionResponse(
        UUID subId,
        SubscriptionStatus subStatus,
        String paymentId,
        String paymentStatus,
        long amount,
        String currency,
        Instant paymentCreatedAt
) {

    public static CheckoutSubscriptionResponse from(
            Subscription subscription,
            PaymentResult payment
    ) {
        return new CheckoutSubscriptionResponse(
                subscription.getId(),
                subscription.getStatus(),
                payment.paymentId(),
                payment.status().name(),
                payment.amount(),
                payment.currency(),
                payment.createdAt()
        );
    }
}