package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.UserRegisteredEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import com.thropic.talki.notification.infrastructure.messaging.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer mínimo de user.registered (SB1-05-B): da la bienvenida al usuario.
 * No consume el email del evento para avisos (privacidad); solo username.
 *
 * deletion.completed: pendiente de la puerta de privacidad G-PRIV
 * (US27, US33, TS06) — no se consume en este sprint.
 */
@Component
public class UserRegisteredNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredNotificationConsumer.class);

    private final EventDeduplicator deduplicator;
    private final NotificationChannel notificationChannel;

    public UserRegisteredNotificationConsumer(EventDeduplicator deduplicator,
                                              NotificationChannel notificationChannel) {
        this.deduplicator = deduplicator;
        this.notificationChannel = notificationChannel;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_USER_REG)
    public void onUserRegistered(UserRegisteredEvent event) {
        if (deduplicator.isDuplicate(event.getEventId())) {
            log.info("[notification-service] user.registered duplicado, se ignora — eventId={}",
                    event.getEventId());
            return;
        }

        log.info("[notification-service] Usuario registrado — userId={}", event.getUserId());

        notificationChannel.send(
                String.valueOf(event.getUserId()),
                "¡Bienvenido a Talki!",
                "Hola " + event.getUsername() + ", tu cuenta fue creada con éxito"
        );
    }
}
