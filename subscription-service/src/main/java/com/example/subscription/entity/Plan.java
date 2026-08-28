package com.example.subscription.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {
    @Id
    @GeneratedValue
    UUID id;

    @Column(
            nullable = false,
            unique = true,
            length = 64
    )
    private String code;

    @Column(
            nullable = false,
            length = 128
    )
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(
            nullable = false,
            length = 3
    )
    private String currency;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(nullable = false)
    private boolean active;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Plan() {}

    public Plan(
            String code,
            String name,
            long price,
            String currency,
            int durationDays
    ) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Plan code must not be blank");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Plan name must not be blank");
        }

        if (price < 0) {
            throw new IllegalArgumentException("Plan price must not be negative");
        }

        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must contain three uppercase letters");
        }

        if (durationDays <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }

        this.code = code;
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.durationDays = durationDays;
        this.active = true;
    }


    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public long getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}