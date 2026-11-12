package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.UserAccountEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetRequestedConsumerTest {

    private final EventDeduplicator deduplicator = new EventDeduplicator();
    private final NotificationChannel channel = mock(NotificationChannel.class);
    private final PasswordResetRequestedConsumer consumer =
            new PasswordResetRequestedConsumer(deduplicator, channel);

    @Test
    void onPasswordResetRequested_shouldSendNoticeWithRawToken() {
        consumer.onPasswordResetRequested(buildEvent(7L, "evt-1", "reset-abc"));

        verify(channel).send(
                eq("7"),
                eq(PasswordResetRequestedConsumer.TITLE),
                contains("reset-abc"));
    }

    @Test
    void whenSameEventDeliveredTwice_shouldSendOnlyOneNotification() {
        UserAccountEvent event = buildEvent(7L, "evt-1", "reset-abc");

        consumer.onPasswordResetRequested(event);
        consumer.onPasswordResetRequested(event);

        verify(channel, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void whenDifferentEventIds_shouldSendBothNotifications() {
        consumer.onPasswordResetRequested(buildEvent(7L, "evt-1", "reset-1"));
        consumer.onPasswordResetRequested(buildEvent(7L, "evt-2", "reset-2"));

        verify(channel, times(2)).send(anyString(), anyString(), anyString());
    }

    private UserAccountEvent buildEvent(Long userId, String eventId, String token) {
        UserAccountEvent event = new UserAccountEvent();
        event.setEventId(eventId);
        event.setEventType("user.password-reset-requested");
        event.setOccurredAt(Instant.now());
        event.setUserId(userId);
        event.setEmail("manuel@upc.edu.pe");
        event.setToken(token);
        return event;
    }
}
