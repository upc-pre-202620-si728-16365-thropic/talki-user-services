package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.UserRegisteredEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserRegisteredNotificationConsumerTest {

    private final EventDeduplicator deduplicator = new EventDeduplicator();
    private final NotificationChannel channel = mock(NotificationChannel.class);
    private final UserRegisteredNotificationConsumer consumer =
            new UserRegisteredNotificationConsumer(deduplicator, channel);

    @Test
    void onUserRegistered_shouldSendWelcomeNotificationWithUsername() {
        consumer.onUserRegistered(buildEvent(7L, "ManuelTumi"));

        verify(channel).send(
                eq("7"),
                eq("¡Bienvenido a Talki!"),
                contains("Hola ManuelTumi, tu cuenta fue creada con éxito"));
    }

    @Test
    void whenSameEventDeliveredTwice_shouldSendOnlyOneNotification() {
        UserRegisteredEvent event = buildEvent(7L, "ManuelTumi");

        consumer.onUserRegistered(event);
        consumer.onUserRegistered(event);

        verify(channel, times(1)).send(anyString(), anyString(), anyString());
    }

    private UserRegisteredEvent buildEvent(Long userId, String username) {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setEventId("evt-user-" + userId);
        event.setEventType("user.registered");
        event.setOccurredAt(Instant.now());
        event.setUserId(userId);
        event.setUsername(username);
        event.setAcademicSegment("ciclos_6_10");
        return event;
    }
}
