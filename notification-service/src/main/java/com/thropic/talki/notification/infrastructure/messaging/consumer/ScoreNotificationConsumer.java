package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.ScoringCompletedEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import com.thropic.talki.notification.infrastructure.messaging.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ScoreNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScoreNotificationConsumer.class);

    private final EventDeduplicator deduplicator;
    private final NotificationChannel notificationChannel;

    public ScoreNotificationConsumer(EventDeduplicator deduplicator,
                                     NotificationChannel notificationChannel) {
        this.deduplicator = deduplicator;
        this.notificationChannel = notificationChannel;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SCORE)
    public void onScoringCompleted(ScoringCompletedEvent event) {
        if (deduplicator.isDuplicate(event.getEventId())) {
            log.info("[notification-service] scoring.completed duplicado, se ignora — eventId={}",
                    event.getEventId());
            return;
        }

        log.info("[notification-service] Sesión completada — userId={} sessionId={} score={}",
                event.getUserId(), event.getSessionId(), event.getOverallScore());

        notificationChannel.send(
                event.getUserId(),
                "Resultado de sesión",
                "Tu sesión " + event.getSessionId() + " obtuvo un puntaje de "
                        + event.getOverallScore() + "/100"
        );
    }
}
