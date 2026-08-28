package com.example.subscription.errors.subscription;

public class SubscriptionAlreadyExistsException extends RuntimeException {
    public SubscriptionAlreadyExistsException(String idempotencyKey) {
        super("Subscription with code " + idempotencyKey + " already exists");
    }
}
