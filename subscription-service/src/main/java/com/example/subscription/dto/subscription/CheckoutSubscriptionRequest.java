package com.example.subscription.dto.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckoutSubscriptionRequest(

        @NotBlank
        @Size(max = 128)
        String paymentMethodId

) {
}