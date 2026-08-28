package com.example.payment.workers;

import com.example.payment.dto.kafka.ClaimedOutboxEvent;
import com.example.payment.usecase.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class OutboxPublisherWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherWorker.class);

    private final OutboxService outboxService;
    private final PaymentEventPublisher publisher;
    private final int batchSize;

    public OutboxPublisherWorker(
            OutboxService outboxService,
            PaymentEventPublisher publisher,
            @Value("${payment.outbox.batch-size:100}")
            int batchSize
    ) {
        this.outboxService = outboxService;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.poll-delay-ms:1000}")
    public void publishEvents() {
        List<ClaimedOutboxEvent> events = outboxService.claimBatch(batchSize);

        for (ClaimedOutboxEvent event : events) {
            try {
                publisher.publish(event);
                outboxService.markPublished(event.eventId());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                markFailed(event, exception);
                return;
            } catch (Exception exception) {
                markFailed(event, exception);
            }
        }
    }

    @Scheduled(fixedDelayString = "${payment.outbox.recovery-delay-ms:60000}")
    public void recoverStuckEvents() {
        int recovered = outboxService.recoverStuckEvents(Duration.ofMinutes(5));

        if (recovered > 0) {
            log.warn("Recovered stuck outbox events: count={}", recovered);
        }
    }

    private void markFailed(
            ClaimedOutboxEvent event,
            Exception exception
    ) {
        log.error(
                "Could not publish outbox event: eventId={}, paymentId={}",
                event.eventId(),
                event.paymentId(),
                exception
        );

        String error = exception.getClass().getSimpleName() + ": " + exception.getMessage();

        try {
            outboxService.markFailed(event.eventId(), error);
        } catch (RuntimeException updateException) {
            log.error(
                    "Could not mark outbox event as failed: eventId={}",
                    event.eventId(),
                    updateException
            );
        }
    }
}