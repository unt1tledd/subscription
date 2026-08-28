package com.example.payment.processor;

public interface PaymentProcessor {

    ProcessingResult process(
            String paymentMethodId,
            long amountMinor,
            String currency,
            String idempotencyKey
    );

    ProcessingResult getStatus(
            String idempotencyKey
    );
}