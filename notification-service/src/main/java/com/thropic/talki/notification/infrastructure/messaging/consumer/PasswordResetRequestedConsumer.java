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
 * Consumer de user.password-reset-requested (routing key PROPUESTA pendiente
 * de ratificación): entrega al usuario el token de recuperación. Deduplicación
 * por eventId (redelivery del broker → un solo aviso).
 */
@Component
public class PasswordResetRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRequestedConsumer.class);

    static final String TITLE = "Recuperación de contraseña";
    static final String BODY_PREFIX = "Usa este token para restablecer tu contraseña (válido 30 minutos): ";

    private final EventDeduplicator deduplicator;
    private final NotificationChannel notificationChannel;

    public PasswordResetRequestedConsumer(EventDeduplicator deduplicator,
                                          NotificationChannel notificationChannel) {
        this.deduplicator = deduplicator;
        this.notificationChannel = notificationChannel;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PASSWORD_RESET)
    public void onPasswordResetRequested(UserAccountEvent event) {
        if (deduplicator.isDuplicate(event.getEventId())) {
            log.info("[notification-service] user.password-reset-requested duplicado, se ignora — eventId={}",
                    event.getEventId());
            return;
        }

        log.info("[notification-service] Recuperación de contraseña solicitada — userId={}", event.getUserId());

        notificationChannel.send(
                String.valueOf(event.getUserId()),
                TITLE,
                BODY_PREFIX + event.getToken()
        );
    }
}
