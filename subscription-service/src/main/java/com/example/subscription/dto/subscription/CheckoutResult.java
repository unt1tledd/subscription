package com.example.subscription.dto.subscription;

import com.example.subscription.entity.Subscription;

public record CheckoutResult(
        Subscription subscription,
        PaymentResult payment
) {
}