package com.thropic.talki.identity.application.events;

import com.thropic.talki.identity.domain.event.VoiceConsentEvent;

/**
 * Puerto de salida (Hexagonal / DDD) para publicar los eventos de
 * consentimiento de voz (US33). La capa de aplicación depende de esta
 * abstracción; la implementación RabbitMQ vive en infraestructura.
 */
public interface ConsentEventPublisher {

    void publish(VoiceConsentEvent event);
}
