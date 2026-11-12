package com.thropic.talki.identity.domain.exception;

/** Excedió el límite de intentos de login (HTTP 429). */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Demasiados intentos fallidos. Reintenta en " + retryAfterSeconds + " segundos");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
