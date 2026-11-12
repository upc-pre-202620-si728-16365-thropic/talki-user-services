package com.thropic.talki.identity.application.events;

import com.thropic.talki.identity.domain.event.UserAccountEvent;

/**
 * Puerto de salida (Hexagonal / DDD) para los eventos del ciclo de vida de
 * la cuenta (verificación de correo, recuperación de contraseña, perfil).
 * La capa de aplicación depende de esta abstracción; la implementación
 * RabbitMQ vive en infraestructura (patrón ConsentEventPublisher).
 */
public interface AccountEventPublisher {

    void publish(UserAccountEvent event);
}
