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
 * Consumer de user.email-verification-requested (routing key PROPUESTA
 * pendiente de ratificación): entrega al usuario el token de verificación.
 * Deduplicación por eventId (redelivery del broker → un solo aviso).
 */
@Component
public class EmailVerificationRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationRequestedConsumer.class);

    static final String TITLE = "Verifica tu correo";
    static final String BODY_PREFIX = "Confirma tu correo con este token (válido 24 horas): ";

    private final EventDeduplicator deduplicator;
    private final NotificationChannel notificationChannel;

    public EmailVerificationRequestedConsumer(EventDeduplicator deduplicator,
                                              NotificationChannel notificationChannel) {
        this.deduplicator = deduplicator;
        this.notificationChannel = notificationChannel;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_EMAIL_VERIFICATION)
    public void onEmailVerificationRequested(UserAccountEvent event) {
        if (deduplicator.isDuplicate(event.getEventId())) {
            log.info("[notification-service] user.email-verification-requested duplicado, se ignora — eventId={}",
                    event.getEventId());
            return;
        }

        log.info("[notification-service] Verificación de correo solicitada — userId={}", event.getUserId());

        notificationChannel.send(
                String.valueOf(event.getUserId()),
                TITLE,
                BODY_PREFIX + event.getToken()
        );
    }
}
