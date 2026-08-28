package com.example.subscription.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            nullable = false,
            columnDefinition = "sub_status"
    )
    private SubscriptionStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean autoRenew;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(
            name = "idempotency_key",
            nullable = false,
            unique = true,
            updatable = false,
            length = 128
    )
    private String idempotencyKey;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Subscription() {}

    public Subscription(
            long userId,
            Plan plan,
            boolean autoRenew,
            String idempotencyKey
    ) {
        if (userId <= 0) {
            throw new IllegalArgumentException(
                    "User id must be positive"
            );
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key must not be blank"
            );
        }

        this.userId = userId;
        this.plan = Objects.requireNonNull(
                plan,
                "Plan must not be null"
        );
        this.autoRenew = autoRenew;
        this.idempotencyKey = idempotencyKey;

        this.status = SubscriptionStatus.NEW;
    }

    public void activate() {
        if (status != SubscriptionStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                    "Only pending subscription can be activated"
            );
        }

        Instant now = Instant.now();

        this.status = SubscriptionStatus.ACTIVE;
        this.startedAt = now;
        this.expiresAt = now.plus(
                plan.getDurationDays(),
                ChronoUnit.DAYS
        );
    }

    public void markPaymentFailed() {
        if (status != SubscriptionStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                    "Subscription is not waiting for payment"
            );
        }

        this.status = SubscriptionStatus.FAILED_PAYMENT;
        this.autoRenew = false;
    }

    public void cancelAutoRenew() {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only active subscription can be cancelled"
            );
        }

        this.autoRenew = false;
        this.cancelledAt = Instant.now();
    }

    public void cancelled() {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only active subscription can expire"
            );
        }

        this.status = SubscriptionStatus.CANCELLED;
        this.autoRenew = false;
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();

        if (status == null) {
            status = SubscriptionStatus.NEW;
        }

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

    public long getUserId() {
        return userId;
    }

    public Plan getPlan() {
        return plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public long getVersion() {
        return version;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void addAutoRenew() {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only active subscription can enable auto-renew"
            );
        }

        this.autoRenew = true;
        this.cancelledAt = null;
    }

    public void expire() {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only active subscription can expire"
            );
        }

        this.status = SubscriptionStatus.CANCELLED;
        this.autoRenew = false;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public void paymentFailed() {
        if (status != SubscriptionStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                    "Only pending subscription can fail payment"
            );
        }

        this.status = SubscriptionStatus.FAILED_PAYMENT;
    }

    public void startPayment() {
        if (status != SubscriptionStatus.NEW
                && status != SubscriptionStatus.FAILED_PAYMENT) {
            throw new IllegalStateException(
                    "Cannot start payment for subscription with status: "
                            + status
            );
        }

        this.status = SubscriptionStatus.PENDING_PAYMENT;
    }
}
