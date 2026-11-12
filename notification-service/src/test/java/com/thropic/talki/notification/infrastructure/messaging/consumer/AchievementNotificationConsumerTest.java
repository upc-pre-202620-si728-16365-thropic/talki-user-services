package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.domain.event.AchievementUnlockedEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AchievementNotificationConsumerTest {

    private final EventDeduplicator deduplicator = new EventDeduplicator();

    @Test
    void onAchievementUnlocked_whenEventReceived_shouldProcessWithoutException() {
        AchievementNotificationConsumer consumer =
                spy(new AchievementNotificationConsumer(deduplicator));
        AchievementUnlockedEvent event = buildEvent(
                "user-001", "ach-streak-7",
                "Constancia Semanal", "Practicaste 7 días seguidos"
        );

        assertThatNoException().isThrownBy(() -> consumer.onAchievementUnlocked(event));
    }

    @Test
    void onAchievementUnlocked_whenDescriptionIsNull_shouldNotThrow() {
        AchievementNotificationConsumer consumer =
                spy(new AchievementNotificationConsumer(deduplicator));
        AchievementUnlockedEvent event = buildEvent(
                "user-002", "ach-first-pitch",
                "Primer Pitch", null
        );

        assertThatNoException().isThrownBy(() -> consumer.onAchievementUnlocked(event));
    }

    @Test
    void onAchievementUnlocked_whenAchievementNameContainsSpecialChars_shouldNotThrow() {
        AchievementNotificationConsumer consumer =
                spy(new AchievementNotificationConsumer(deduplicator));
        AchievementUnlockedEvent event = buildEvent(
                "user-003", "ach-thesis-master",
                "¡Defensor de Tesis!", "Completaste 10 sesiones thesis_defense"
        );

        assertThatNoException().isThrownBy(() -> consumer.onAchievementUnlocked(event));
    }

    @Test
    void onAchievementUnlocked_whenSameEventDeliveredTwice_shouldSendOnlyOneNotification() {
        AchievementNotificationConsumer consumer =
                spy(new AchievementNotificationConsumer(deduplicator));
        AchievementUnlockedEvent event = buildEvent(
                "user-001", "ach-streak-7",
                "Constancia Semanal", "Practicaste 7 días seguidos"
        );

        consumer.onAchievementUnlocked(event);
        consumer.onAchievementUnlocked(event); // redelivery con el mismo eventId

        // Un solo aviso: el segundo vistazo se descarta por deduplicación.
        verify(consumer, times(1)).sendAchievementNotification(anyString(), anyString(), any());
    }

    @Test
    void onAchievementUnlocked_whenDifferentEvents_shouldSendBothNotifications() {
        AchievementNotificationConsumer consumer =
                spy(new AchievementNotificationConsumer(deduplicator));

        consumer.onAchievementUnlocked(buildEvent("user-001", "ach-a", "Logro A", "desc A"));
        consumer.onAchievementUnlocked(buildEvent("user-001", "ach-b", "Logro B", "desc B"));

        verify(consumer, times(2)).sendAchievementNotification(anyString(), anyString(), any());
    }

    @Test
    void onAchievementUnlocked_whenUserIdIsEmpty_shouldStillProcess() {
        AchievementNotificationConsumer consumer =
                spy(new AchievementNotificationConsumer(deduplicator));
        AchievementUnlockedEvent event = buildEvent(
                "", "ach-anonymous",
                "Logro fantasma", "Sin usuario"
        );

        assertThatNoException().isThrownBy(() -> consumer.onAchievementUnlocked(event));
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
