package com.example.subscription.dto.plan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreatePlanRequest(

        @NotBlank
        @Size(max = 64)
        String code,

        @NotBlank
        @Size(max = 128)
        String name,

        @PositiveOrZero
        long price,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}")
        String currency,

        @Positive
        int durationDays
){}