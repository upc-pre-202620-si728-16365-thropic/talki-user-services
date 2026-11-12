package com.thropic.talki.notification.infrastructure.messaging.consumer;

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

    public ScoreNotificationConsumer(EventDeduplicator deduplicator) {
        this.deduplicator = deduplicator;
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

        sendScoreNotification(event.getUserId(), event.getOverallScore(), event.getSessionId());
    }

    void sendScoreNotification(String userId, int score, String sessionId) {
        log.info("[notification-service] PUSH → userId={} mensaje='Tu sesión {} obtuvo un puntaje de {}/100'",
                userId, sessionId, score);
    }
}
