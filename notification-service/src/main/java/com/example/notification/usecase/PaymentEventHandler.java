package com.example.notification.usecase;

import com.example.notification.dto.PaymentStatusChangedEvent;
import com.example.notification.entity.Notification;
import com.example.notification.errors.InvalidPaymentEventException;
import com.example.notification.repository.InboxRepository;
import com.example.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class PaymentEventHandler {

    private final InboxRepository inboxRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public PaymentEventHandler(
            InboxRepository inboxRepository,
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper
    ) {
        this.inboxRepository = inboxRepository;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handle(PaymentStatusChangedEvent event, String rawMessage) {
        int inserted = inboxRepository.tryRegister(
                event.eventId(),
                event.paymentId(),
                event.paymentStatus().name(),
                rawMessage
        );

        if (inserted == 0) return;

        Map<String, Object> payload =
                objectMapper.convertValue(
                        event.payload(),
                        new TypeReference<>() {}
                );

        Notification notification = Notification.from(event, payload);

        notificationRepository.save(notification);

        int updated = inboxRepository.markProcessed(event.eventId());

        if (updated != 1) {
            throw new InvalidPaymentEventException(
                    "Could not mark inbox event as processed: " + event.eventId()
            );
        }
    }
}