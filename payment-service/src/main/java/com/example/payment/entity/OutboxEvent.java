package com.example.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    @Column(
            name = "payment_id",
            nullable = false,
            updatable = false
    )
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "payment_status",
            nullable = false,
            updatable = false,
            columnDefinition = "payment_status"
    )
    private PaymentStatus paymentStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "payload",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "status",
            nullable = false,
            columnDefinition = "outbox_status"
    )
    private OutboxStatus status;

    @Column(
            name = "attempts",
            nullable = false
    )
    private int attempts;

    @Column(
            name = "next_attempt_at",
            nullable = false
    )
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {}

    private OutboxEvent(
            UUID paymentId,
            PaymentStatus paymentStatus,
            Map<String, Object> payload
    ) {
        this.paymentId = Objects.requireNonNull(
                paymentId,
                "paymentId must not be null"
        );

        this.paymentStatus = Objects.requireNonNull(
                paymentStatus,
                "paymentStatus must not be null"
        );

        this.payload = new LinkedHashMap<>(
                Objects.requireNonNull(
                        payload,
                        "payload must not be null"
                )
        );

        this.status = OutboxStatus.NEW;
        this.attempts = 0;
    }

    public static OutboxEvent paymentStatusChanged(
            Payment payment
    ) {
        Objects.requireNonNull(
                payment,
                "payment must not be null"
        );

        PaymentStatus paymentStatus =
                payment.getStatus();

        if (paymentStatus == PaymentStatus.PENDING) {
            throw new IllegalStateException("Outbox event cannot be created for status: " + paymentStatus);
        }

        Map<String, Object> payload = getStringObjectMap(payment, paymentStatus);

        return new OutboxEvent(
                payment.getId(),
                paymentStatus,
                payload
        );
    }

    private static Map<String, Object> getStringObjectMap(Payment payment, PaymentStatus paymentStatus) {
        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put(
                "paymentId",
                payment.getId()
        );

        payload.put(
                "userId",
                payment.getUserId()
        );

        payload.put(
                "amount",
                payment.getAmount()
        );

        payload.put(
                "currency",
                payment.getCurrency()
        );

        payload.put(
                "status",
                paymentStatus.name()
        );

        if (payment.getFailureCode() != null) {
            payload.put(
                    "failureCode",
                    payment.getFailureCode()
            );
        }
        return payload;
    }

    @PrePersist
    private void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = OutboxStatus.NEW;
        }

        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = Instant.now();
    }

    public void markProcessing() {
        if (status != OutboxStatus.NEW
                && status != OutboxStatus.FAILED) {
            throw new IllegalStateException(
                    "Only NEW or FAILED event can be processed"
            );
        }

        status = OutboxStatus.PROCESSING;
        lastError = null;
    }

    public void markPublished() {
        if (status != OutboxStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only PROCESSING event can be published"
            );
        }

        status = OutboxStatus.PUBLISHED;
        publishedAt = Instant.now();
        lastError = null;
    }

    public void markFailed(
            String error,
            Instant nextAttemptAt
    ) {
        if (status != OutboxStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only PROCESSING event can fail"
            );
        }

        this.status = OutboxStatus.FAILED;
        this.attempts++;
        this.lastError = Objects.requireNonNull(
                error,
                "error must not be null"
        );

        this.nextAttemptAt = Objects.requireNonNull(
                nextAttemptAt,
                "nextAttemptAt must not be null"
        );
    }

    public void markFailedWithBackoff(
            String error
    ) {
        long delayMinutes = Math.min(
                1L << Math.min(attempts, 5),
                30L
        );

        markFailed(
                error,
                Instant.now().plus(
                        delayMinutes,
                        ChronoUnit.MINUTES
                )
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public Map<String, Object> getPayload() {
        return Map.copyOf(payload);
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}