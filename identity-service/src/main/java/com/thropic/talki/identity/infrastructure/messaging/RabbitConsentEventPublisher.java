package com.thropic.talki.identity.infrastructure.messaging;

import com.thropic.talki.identity.application.events.ConsentEventPublisher;
import com.thropic.talki.identity.domain.event.VoiceConsentEvent;
import com.thropic.talki.identity.infrastructure.messaging.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida RabbitMQ del puerto {@link ConsentEventPublisher}.
 * Publica sobre el exchange topic talki.events; la routing key es el
 * eventType (voice.consent.registered / voice.consent.withdrawn,
 * PROPUESTAS pendientes de ratificación en el informe).
 */
@Component
public class RabbitConsentEventPublisher implements ConsentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitConsentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitConsentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(VoiceConsentEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, event.getEventType(), event);
        log.info("[identity-service] Published {} — userId={} consentId={}",
                event.getEventType(), event.getUserId(), event.getConsentId());
    }
}
