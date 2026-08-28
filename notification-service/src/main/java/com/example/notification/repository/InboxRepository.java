package com.example.notification.repository;

import com.example.notification.entity.InboxEvent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InboxRepository extends Repository<InboxEvent, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO inbox_events (
                        event_id,
                        payment_id,
                        payment_status,
                        payload,
                        status,
                        attempts,
                        received_at,
                        updated_at
                    )
                    VALUES (
                        :eventId,
                        :paymentId,
                        CAST(:paymentStatus AS incoming_payment_status),
                        CAST(:payload AS jsonb),
                        'PROCESSING',
                        0,
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (event_id)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int tryRegister(
            @Param("eventId") UUID eventId,
            @Param("paymentId") UUID paymentId,
            @Param("paymentStatus") String paymentStatus,
            @Param("payload") String payload
    );

    @Modifying
    @Query(
            value = """
                    UPDATE inbox_events
                    SET status = 'PROCESSED',
                        processed_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP,
                        last_error = NULL
                    WHERE event_id = :eventId
                      AND status = 'PROCESSING'
                    """,
            nativeQuery = true
    )
    int markProcessed(@Param("eventId") UUID eventId);
}