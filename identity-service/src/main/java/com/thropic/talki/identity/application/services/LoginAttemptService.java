package com.thropic.talki.identity.application.services;

import com.thropic.talki.identity.domain.exception.TooManyLoginAttemptsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos de login por email: ventana deslizante en memoria con
 * TTL. Al superar el máximo dentro de la ventana, lanza 429 indicando cuándo
 * reintentar (segundos hasta que el intento más viejo salga de la ventana).
 */
// ponytail: mapa en memoria (una sola instancia); usar Redis/CAP si se escala horizontalmente.
@Component
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Duration window;
    private final Map<String, Deque<Long>> attemptsByEmail = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(WINDOW);
    }

    /** Visibilidad de paquete para tests: ventana corta sin esperar 15 minutos. */
    LoginAttemptService(Duration window) {
        this.window = window;
    }

    public void assertAllowed(String email) {
        Deque<Long> attempts = attemptsByEmail.get(email.toLowerCase());
        if (attempts == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (attempts) {
            attempts.removeIf(t -> now - t > window.toMillis());
            if (attempts.size() >= MAX_ATTEMPTS) {
                long oldest = attempts.peekFirst();
                long retryAfterSeconds = Math.max(1,
                        (window.toMillis() - (now - oldest) + 999) / 1000);
                throw new TooManyLoginAttemptsException(retryAfterSeconds);
            }
        }
    }

    public void recordFailure(String email) {
        Deque<Long> attempts = attemptsByEmail.computeIfAbsent(email.toLowerCase(), k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (attempts) {
            attempts.removeIf(t -> now - t > window.toMillis());
            attempts.addLast(now);
            while (attempts.size() > MAX_ATTEMPTS) {
                attempts.pollFirst();
            }
        }
    }

    public void reset(String email) {
        attemptsByEmail.remove(email.toLowerCase());
    }
}
