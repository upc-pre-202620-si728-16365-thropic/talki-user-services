package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.UserAccountEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProfileUpdatedConsumerTest {

    private final EventDeduplicator deduplicator = new EventDeduplicator();
    private final NotificationChannel channel = mock(NotificationChannel.class);
    private final ProfileUpdatedConsumer consumer =
            new ProfileUpdatedConsumer(deduplicator, channel);

    @Test
    void onProfileUpdated_shouldSendNoticeWithNewUsernameAndSegment() {
        consumer.onProfileUpdated(buildEvent(7L, "evt-1", "NuevoNombre", "ciclos_1_5"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(channel).send(eq("7"), eq(ProfileUpdatedConsumer.TITLE), body.capture());
        assertThat(body.getValue()).contains("NuevoNombre").contains("ciclos_1_5");
    }

    @Test
    void whenSameEventDeliveredTwice_shouldSendOnlyOneNotification() {
        UserAccountEvent event = buildEvent(7L, "evt-1", "NuevoNombre", "ciclos_1_5");

        consumer.onProfileUpdated(event);
        consumer.onProfileUpdated(event);

        verify(channel, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void whenDifferentEventIds_shouldSendBothNotifications() {
        consumer.onProfileUpdated(buildEvent(7L, "evt-1", "Nombre1", "ciclos_1_5"));
        consumer.onProfileUpdated(buildEvent(7L, "evt-2", "Nombre2", "ciclos_6_10"));

        verify(channel, times(2)).send(anyString(), anyString(), anyString());
    }

    private UserAccountEvent buildEvent(Long userId, String eventId, String username, String segment) {
        UserAccountEvent event = new UserAccountEvent();
        event.setEventId(eventId);
        event.setEventType("user.profile-updated");
        event.setOccurredAt(Instant.now());
        event.setUserId(userId);
        event.setUsername(username);
        event.setAcademicSegment(segment);
        return event;
    }
}
