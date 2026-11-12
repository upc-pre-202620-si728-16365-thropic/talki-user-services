package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.domain.event.ScoringCompletedEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ScoreNotificationConsumerTest {

    private final EventDeduplicator deduplicator = new EventDeduplicator();

    @Test
    void onScoringCompleted_whenEventReceived_shouldProcessWithoutException() {
        ScoreNotificationConsumer consumer = spy(new ScoreNotificationConsumer(deduplicator));

        consumer.onScoringCompleted(buildEvent("user-001", "session-abc", 85));

        verify(consumer).sendScoreNotification("user-001", 85, "session-abc");
    }

    @Test
    void onScoringCompleted_whenScoreIsZero_shouldStillProcess() {
        ScoreNotificationConsumer consumer = spy(new ScoreNotificationConsumer(deduplicator));

        consumer.onScoringCompleted(buildEvent("user-002", "session-xyz", 0));

        verify(consumer).sendScoreNotification("user-002", 0, "session-xyz");
    }

    @Test
    void onScoringCompleted_whenScoreIsPerfect_shouldProcess() {
        ScoreNotificationConsumer consumer = spy(new ScoreNotificationConsumer(deduplicator));

        consumer.onScoringCompleted(buildEvent("user-003", "session-perfect", 100));

        verify(consumer).sendScoreNotification("user-003", 100, "session-perfect");
    }

    @Test
    void onScoringCompleted_whenSameEventDeliveredTwice_shouldSendOnlyOneNotification() {
        ScoreNotificationConsumer consumer = spy(new ScoreNotificationConsumer(deduplicator));
        ScoringCompletedEvent event = buildEvent("user-001", "session-abc", 85);

        consumer.onScoringCompleted(event);
        consumer.onScoringCompleted(event); // redelivery con el mismo eventId

        // Un solo aviso: el segundo vistazo se descarta por deduplicación.
        verify(consumer, times(1)).sendScoreNotification(anyString(), anyInt(), anyString());
    }

    @Test
    void onScoringCompleted_whenDifferentEvents_shouldSendBothNotifications() {
        ScoreNotificationConsumer consumer = spy(new ScoreNotificationConsumer(deduplicator));

        consumer.onScoringCompleted(buildEvent("user-001", "session-a", 80));
        consumer.onScoringCompleted(buildEvent("user-001", "session-b", 90));

        verify(consumer, times(2)).sendScoreNotification(anyString(), anyInt(), anyString());
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
