package com.thropic.talki.notification.application.port;

/**
 * Puerto de salida para entregar un aviso al usuario (SB1-05-B).
 * La capa de mensajería no conoce el medio concreto (log, WebSocket, correo);
 * el adaptador vive en infrastructure.channel.
 *
 * Hoy existe un único adaptador: {@code LogNotificationChannel}.
 * WebSocket y correo son propuestos, no configurados (ver README).
 */
public interface NotificationChannel {

    void send(String userId, String title, String body);
}
