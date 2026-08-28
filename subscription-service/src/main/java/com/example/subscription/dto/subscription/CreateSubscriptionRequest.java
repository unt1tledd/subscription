package com.example.subscription.dto.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateSubscriptionRequest (
        @Positive
        long userId,

        @NotBlank
        @Size(max = 64)
        String planCode,

        boolean autoRenew
) {}
