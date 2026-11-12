package com.thropic.talki.identity.domain.exception;

/**
 * Credenciales inválidas (HTTP 401). Mensaje único y genérico: no revela
 * si falló el email o la contraseña (evita enumeración de usuarios).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}
