package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.UserAccountEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import com.thropic.talki.notification.infrastructure.messaging.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer de user.profile-updated (routing key PROPUESTA pendiente de
 * ratificación): informa al usuario del cambio de su perfil (solo los
 * nuevos username/academicSegment). Deduplicación por eventId.
 */
@Component
public class ProfileUpdatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProfileUpdatedConsumer.class);

    static final String TITLE = "Perfil actualizado";

    private final EventDeduplicator deduplicator;
    private final NotificationChannel notificationChannel;

    public ProfileUpdatedConsumer(EventDeduplicator deduplicator,
                                  NotificationChannel notificationChannel) {
        this.deduplicator = deduplicator;
        this.notificationChannel = notificationChannel;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PROFILE_UPDATED)
    public void onProfileUpdated(UserAccountEvent event) {
        if (deduplicator.isDuplicate(event.getEventId())) {
            log.info("[notification-service] user.profile-updated duplicado, se ignora — eventId={}",
                    event.getEventId());
            return;
        }

        log.info("[notification-service] Perfil actualizado — userId={}", event.getUserId());

        notificationChannel.send(
                String.valueOf(event.getUserId()),
                TITLE,
                "Tu perfil fue actualizado: username=" + event.getUsername()
                        + ", segmento=" + event.getAcademicSegment()
        );
    }
}
