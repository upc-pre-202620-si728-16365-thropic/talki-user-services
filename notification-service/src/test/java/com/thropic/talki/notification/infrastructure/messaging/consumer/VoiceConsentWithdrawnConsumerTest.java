package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.VoiceConsentWithdrawnEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VoiceConsentWithdrawnConsumerTest {

    private final EventDeduplicator deduplicator = new EventDeduplicator();
    private final NotificationChannel channel = mock(NotificationChannel.class);
    private final VoiceConsentWithdrawnConsumer consumer =
            new VoiceConsentWithdrawnConsumer(deduplicator, channel);

    @Test
    void onVoiceConsentWithdrawn_shouldSendBlockedProcessingNotice() {
        consumer.onVoiceConsentWithdrawn(buildEvent(7L, "evt-1"));

        verify(channel).send(
                eq("7"),
                eq(VoiceConsentWithdrawnConsumer.TITLE),
                eq(VoiceConsentWithdrawnConsumer.BODY));
    }

    @Test
    void whenSameEventDeliveredTwice_shouldSendOnlyOneNotification() {
        consumer.onVoiceConsentWithdrawn(buildEvent(7L, "evt-1"));
        consumer.onVoiceConsentWithdrawn(buildEvent(7L, "evt-1"));

        verify(channel, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void whenDifferentEventIds_shouldSendBothNotifications() {
        consumer.onVoiceConsentWithdrawn(buildEvent(7L, "evt-1"));
        consumer.onVoiceConsentWithdrawn(buildEvent(7L, "evt-2"));

        verify(channel, times(2)).send(anyString(), anyString(), anyString());
    }

    private VoiceConsentWithdrawnEvent buildEvent(Long userId, String eventId) {
        VoiceConsentWithdrawnEvent event = new VoiceConsentWithdrawnEvent();
        event.setEventId(eventId);
        event.setEventType("voice.consent.withdrawn");
        event.setOccurredAt(Instant.now());
        event.setUserId(userId);
        event.setPolicyVersion("v1");
        event.setScope("voice_processing");
        event.setConsentId("00000000-0000-0000-0000-00000000000" + userId);
        return event;
    }
}
