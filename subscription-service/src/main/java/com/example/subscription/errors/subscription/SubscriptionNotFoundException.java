package com.example.subscription.errors.subscription;

import java.util.UUID;

public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(UUID id) {
        super("Subscription with id '%s' was not found".formatted(id));
    }
}