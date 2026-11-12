package com.thropic.talki.identity.infrastructure.messaging;

import com.thropic.talki.identity.application.events.UserRegisteredEventPublisher;
import com.thropic.talki.identity.domain.event.UserRegisteredEvent;
import com.thropic.talki.identity.infrastructure.messaging.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida RabbitMQ del puerto {@link UserRegisteredEventPublisher}.
 * Publica {@code user.registered} en el exchange {@code talki.events} para que
 * los consumidores downstream proyecten localmente al usuario.
 */
@Component
public class RabbitUserRegisteredPublisher implements UserRegisteredEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitUserRegisteredPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitUserRegisteredPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(UserRegisteredEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.USER_REGISTERED_KEY,
                event
        );
        // No se loguea el email del evento (privacidad, SB1-07-D).
        log.info("[identity-service] Published user.registered — userId={}", event.getUserId());
    }
}
