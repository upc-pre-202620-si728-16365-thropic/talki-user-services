package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.AchievementUnlockedEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AchievementNotificationConsumerTest {

    private final EventDeduplicator deduplicator = new EventDeduplicator();
    private final NotificationChannel channel = mock(NotificationChannel.class);
    private final AchievementNotificationConsumer consumer =
            new AchievementNotificationConsumer(deduplicator, channel);

    @Test
    void onAchievementUnlocked_shouldSendNotificationWithNameAndDescription() {
        consumer.onAchievementUnlocked(buildEvent(
                "user-001", "ach-streak-7", "Constancia Semanal", "Practicaste 7 días seguidos"));

        verify(channel).send(
                eq("user-001"),
                eq("¡Logro desbloqueado!"),
                contains("¡Lograste Constancia Semanal! Practicaste 7 días seguidos"));
    }

    @Test
    void onAchievementUnlocked_whenDescriptionIsNull_shouldSendWithoutThrowing() {
        consumer.onAchievementUnlocked(buildEvent("user-002", "ach-first-pitch", "Primer Pitch", null));

        verify(channel).send(eq("user-002"), anyString(), contains("¡Lograste Primer Pitch!"));
    }

    @Test
    void onAchievementUnlocked_whenNameContainsSpecialChars_shouldSend() {
        consumer.onAchievementUnlocked(buildEvent(
                "user-003", "ach-thesis-master",
                "¡Defensor de Tesis!", "Completaste 10 sesiones thesis_defense"));

        verify(channel).send(eq("user-003"), anyString(), contains("¡Defensor de Tesis!"));
    }

    @Test
    void whenSameEventDeliveredTwice_shouldSendOnlyOneNotification() {
        AchievementUnlockedEvent event = buildEvent(
                "user-001", "ach-streak-7", "Constancia Semanal", "Practicaste 7 días seguidos");

        consumer.onAchievementUnlocked(event);
        consumer.onAchievementUnlocked(event); // redelivery con el mismo eventId

        // Un solo aviso: el segundo vistazo se descarta por deduplicación.
        verify(channel, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void whenDifferentEvents_shouldSendBothNotifications() {
        consumer.onAchievementUnlocked(buildEvent("user-001", "ach-a", "Logro A", "desc A"));
        consumer.onAchievementUnlocked(buildEvent("user-001", "ach-b", "Logro B", "desc B"));

        verify(channel, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void onAchievementUnlocked_whenUserIdIsEmpty_shouldStillSend() {
        consumer.onAchievementUnlocked(buildEvent("", "ach-anonymous", "Logro fantasma", "Sin usuario"));

        verify(channel).send(anyString(), anyString(), anyString());
    }

    private AchievementUnlockedEvent buildEvent(String userId, String achievementId,
                                                  String name, String description) {
        AchievementUnlockedEvent event = new AchievementUnlockedEvent();
        event.setEventId("evt-" + achievementId);
        event.setEventType("achievement.unlocked");
        event.setOccurredAt(Instant.now());
        event.setUserId(userId);
        event.setAchievementId(achievementId);
        event.setAchievementName(name);
        event.setDescription(description);
        return event;
    }
}
