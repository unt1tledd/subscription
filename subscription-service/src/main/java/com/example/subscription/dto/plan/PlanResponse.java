package com.example.subscription.dto.plan;

import com.example.subscription.entity.Plan;

import java.time.Instant;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String code,
        String name,
        long priceMinor,
        String currency,
        int durationDays,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.getDurationDays(),
                plan.isActive(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }
}