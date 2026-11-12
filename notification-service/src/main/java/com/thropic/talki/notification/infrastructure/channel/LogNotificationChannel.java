package com.thropic.talki.notification.infrastructure.channel;

import com.thropic.talki.notification.application.port.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Único adaptador del puerto NotificationChannel en Sprint 1: el canal real
 * es el log de la aplicación. WebSocket y correo son propuestos, no configurados.
 */
@Component
public class LogNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationChannel.class);

    @Override
    public void send(String userId, String title, String body) {
        log.info("[notification-service] AVISO → userId={} | {} | {}", userId, title, body);
    }
}
