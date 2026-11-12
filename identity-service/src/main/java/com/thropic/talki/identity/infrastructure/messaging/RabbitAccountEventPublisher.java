package com.thropic.talki.identity.infrastructure.messaging;

import com.thropic.talki.identity.application.events.AccountEventPublisher;
import com.thropic.talki.identity.domain.event.UserAccountEvent;
import com.thropic.talki.identity.infrastructure.messaging.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida RabbitMQ del puerto {@link AccountEventPublisher}.
 * Publica sobre el exchange topic talki.events; la routing key es el
 * eventType (user.email-verification-requested / user.password-reset-requested /
 * user.profile-updated, PROPUESTAS pendientes de ratificación en el informe).
 */
@Component
public class RabbitAccountEventPublisher implements AccountEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitAccountEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitAccountEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(UserAccountEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, event.getEventType(), event);
        // Sin PII: solo userId y eventId (el email va en el payload del evento, no en el log).
        log.info("[identity-service] Published {} — userId={} eventId={}",
                event.getEventType(), event.getUserId(), event.getEventId());
    }
}
