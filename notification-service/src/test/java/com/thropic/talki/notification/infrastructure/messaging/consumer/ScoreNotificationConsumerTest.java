package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.ScoringCompletedEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ScoreNotificationConsumerTest {

    private final EventDeduplicator deduplicator = new EventDeduplicator();
    private final NotificationChannel channel = mock(NotificationChannel.class);
    private final ScoreNotificationConsumer consumer =
            new ScoreNotificationConsumer(deduplicator, channel);

    @Test
    void onScoringCompleted_shouldSendNotificationWithSessionAndScore() {
        consumer.onScoringCompleted(buildEvent("user-001", "session-abc", 85));

        verify(channel).send(
                eq("user-001"),
                eq("Resultado de sesión"),
                contains("Tu sesión session-abc obtuvo un puntaje de 85/100"));
    }

    @Test
    void onScoringCompleted_whenScoreIsZero_shouldStillSend() {
        consumer.onScoringCompleted(buildEvent("user-002", "session-xyz", 0));

        verify(channel).send(eq("user-002"), anyString(), contains("0/100"));
    }

    @Test
    void onScoringCompleted_whenScoreIsPerfect_shouldSend() {
        consumer.onScoringCompleted(buildEvent("user-003", "session-perfect", 100));

        verify(channel).send(eq("user-003"), anyString(), contains("100/100"));
    }

    @Test
    void whenSameEventDeliveredTwice_shouldSendOnlyOneNotification() {
        ScoringCompletedEvent event = buildEvent("user-001", "session-abc", 85);

        consumer.onScoringCompleted(event);
        consumer.onScoringCompleted(event); // redelivery con el mismo eventId

        // Un solo aviso: el segundo vistazo se descarta por deduplicación.
        verify(channel, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void whenDifferentEvents_shouldSendBothNotifications() {
        consumer.onScoringCompleted(buildEvent("user-001", "session-a", 80));
        consumer.onScoringCompleted(buildEvent("user-001", "session-b", 90));

        verify(channel, times(2)).send(anyString(), anyString(), anyString());
    }

    private ScoringCompletedEvent buildEvent(String userId, String sessionId, int score) {
        ScoringCompletedEvent event = new ScoringCompletedEvent();
        event.setEventId("evt-" + userId + "-" + sessionId);
        event.setEventType("scoring.completed");
        event.setOccurredAt(Instant.now());
        event.setUserId(userId);
        event.setSessionId(sessionId);
        event.setOverallScore(score);
        event.setSilenceRatio(0.05);
        event.setWordsPerMinute(130.0);
        return event;
    }
}
