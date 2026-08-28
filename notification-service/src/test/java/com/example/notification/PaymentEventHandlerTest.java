package com.example.notification.usecase;

import com.example.notification.dto.PaymentEventPayload;
import com.example.notification.dto.PaymentStatusChangedEvent;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationStatus;
import com.example.notification.entity.PaymentEventStatus;
import com.example.notification.errors.InvalidPaymentEventException;
import com.example.notification.repository.InboxRepository;
import com.example.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventHandlerTest {

    @Mock
    private InboxRepository inboxRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private PaymentEventHandler paymentEventHandler;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        paymentEventHandler = new PaymentEventHandler(
                inboxRepository,
                notificationRepository,
                objectMapper
        );
    }

    @Test
    void registersEventAndSavesNotification() {
        PaymentStatusChangedEvent event = createEvent();

        String rawMessage = """
                {
                  "eventId": "%s",
                  "paymentId": "%s"
                }
                """.formatted(
                event.eventId(),
                event.paymentId()
        );

        when(inboxRepository.tryRegister(
                event.eventId(),
                event.paymentId(),
                event.paymentStatus().name(),
                rawMessage
        )).thenReturn(1);

        when(inboxRepository.markProcessed(
                event.eventId())).thenReturn(1);

        paymentEventHandler.handle(
                event,
                rawMessage
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();

        assertThat(notification.getEventId()).isEqualTo(event.eventId());

        assertThat(notification.getPaymentId()).isEqualTo(event.paymentId());

        assertThat(notification.getUserId()).isEqualTo(42L);

        assertThat(notification.getPaymentStatus()).isEqualTo(PaymentEventStatus.SUCCEEDED);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.NEW);

        verify(inboxRepository).markProcessed(event.eventId());
    }

    @Test
    void ignoresAlreadyRegisteredEvent() {
        PaymentStatusChangedEvent event = createEvent();

        String rawMessage = "{}";

        when(inboxRepository.tryRegister(
                event.eventId(),
                event.paymentId(),
                event.paymentStatus().name(),
                rawMessage
        )).thenReturn(0);

        paymentEventHandler.handle(
                event,
                rawMessage
        );

        verifyNoInteractions(notificationRepository);

        verify(inboxRepository, never()).markProcessed(any());
    }

    @Test
    void throwsExceptionWhenInboxCannotBeMarkedProcessed() {
        PaymentStatusChangedEvent event = createEvent();

        String rawMessage = "{}";

        when(inboxRepository.tryRegister(
                event.eventId(),
                event.paymentId(),
                event.paymentStatus().name(),
                rawMessage
        )).thenReturn(1);

        when(inboxRepository.markProcessed(event.eventId())).thenReturn(0);

        assertThatThrownBy(() -> paymentEventHandler.handle(
                        event,
                        rawMessage))
                .isInstanceOf(InvalidPaymentEventException.class)
                .hasMessageContaining("Could not mark inbox event as processed")
                .hasMessageContaining(event.eventId().toString());

        verify(notificationRepository).save(any(Notification.class));
    }

    private PaymentStatusChangedEvent createEvent() {
        return new PaymentStatusChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentEventStatus.SUCCEEDED,
                new PaymentEventPayload(
                        42,
                        19_900,
                        "RUB",
                        null,
                        0
                ),
                Instant.now()
        );
    }
}