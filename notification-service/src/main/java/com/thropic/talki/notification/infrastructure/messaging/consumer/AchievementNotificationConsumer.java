package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.domain.event.AchievementUnlockedEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import com.thropic.talki.notification.infrastructure.messaging.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AchievementNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(AchievementNotificationConsumer.class);

    private final EventDeduplicator deduplicator;

    public AchievementNotificationConsumer(EventDeduplicator deduplicator) {
        this.deduplicator = deduplicator;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ACHIEVE)
    public void onAchievementUnlocked(AchievementUnlockedEvent event) {
        if (deduplicator.isDuplicate(event.getEventId())) {
            log.info("[notification-service] achievement.unlocked duplicado, se ignora — eventId={}",
                    event.getEventId());
            return;
        }

        log.info("[notification-service] Logro desbloqueado — userId={} achievement={}",
                event.getUserId(), event.getAchievementName());

        sendAchievementNotification(event.getUserId(), event.getAchievementName(), event.getDescription());
    }

    void sendAchievementNotification(String userId, String name, String description) {
        log.info("[notification-service] PUSH → userId={} mensaje='¡Lograste {}! {}'",
                userId, name, description);
    }
}
