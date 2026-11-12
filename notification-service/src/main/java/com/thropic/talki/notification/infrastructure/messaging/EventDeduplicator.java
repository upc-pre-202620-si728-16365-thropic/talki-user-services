package com.thropic.talki.notification.infrastructure.messaging;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deduplicación de eventos por {@code eventId}: ventana TTL en memoria.
 * El primer vistazo registra el evento; el mismo id dentro de la ventana
 * se considera duplicado (p. ej. redelivery del broker) y no genera aviso.
 */
// ponytail: en memoria (una sola instancia, se pierde al reiniciar); Redis si se escala horizontal.
@Component
public class EventDeduplicator {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final Duration ttl;
    private final Map<String, Long> seenUntil = new ConcurrentHashMap<>();

    public EventDeduplicator() {
        this(DEFAULT_TTL);
    }

    /** Visibilidad de paquete para tests: TTL corto sin esperar minutos. */
    EventDeduplicator(Duration ttl) {
        this.ttl = ttl;
    }

    /** @return true si el eventId ya fue procesado dentro de la ventana TTL. */
    public boolean isDuplicate(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        seenUntil.values().removeIf(expiry -> expiry <= now);
        return seenUntil.putIfAbsent(eventId, now + ttl.toMillis()) != null;
    }
}
