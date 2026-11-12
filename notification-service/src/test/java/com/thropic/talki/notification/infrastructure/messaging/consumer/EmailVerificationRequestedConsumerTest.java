package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.UserAccountEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailVerificationRequestedConsumerTest {

    private final EventDeduplicator deduplicator = new EventDeduplicator();
    private final NotificationChannel channel = mock(NotificationChannel.class);
    private final EmailVerificationRequestedConsumer consumer =
            new EmailVerificationRequestedConsumer(deduplicator, channel);

    @Test
    void onEmailVerificationRequested_shouldSendNoticeWithRawToken() {
        consumer.onEmailVerificationRequested(buildEvent(7L, "evt-1", "token-abc"));

        verify(channel).send(
                eq("7"),
                eq(EmailVerificationRequestedConsumer.TITLE),
                contains("token-abc"));
    }

    @Test
    void whenSameEventDeliveredTwice_shouldSendOnlyOneNotification() {
        UserAccountEvent event = buildEvent(7L, "evt-1", "token-abc");

        consumer.onEmailVerificationRequested(event);
        consumer.onEmailVerificationRequested(event);

        verify(channel, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void whenDifferentEventIds_shouldSendBothNotifications() {
        consumer.onEmailVerificationRequested(buildEvent(7L, "evt-1", "token-1"));
        consumer.onEmailVerificationRequested(buildEvent(7L, "evt-2", "token-2"));

        verify(channel, times(2)).send(anyString(), anyString(), anyString());
    }

    private UserAccountEvent buildEvent(Long userId, String eventId, String token) {
        UserAccountEvent event = new UserAccountEvent();
        event.setEventId(eventId);
        event.setEventType("user.email-verification-requested");
        event.setOccurredAt(Instant.now());
        event.setUserId(userId);
        event.setEmail("manuel@upc.edu.pe");
        event.setToken(token);
        return event;
    }
}
