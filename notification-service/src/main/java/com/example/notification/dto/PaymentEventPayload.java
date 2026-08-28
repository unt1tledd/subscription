package com.example.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEventPayload(
        long userId,
        long amount,
        String currency,
        String failureCode,
        Integer statusCheckAttempts
) {}