package com.thropic.talki.identity.domain.exception;

/** La contraseña no cumple la regla de complejidad (HTTP 400). */
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException() {
        super("La contraseña debe tener al menos 8 caracteres e incluir al menos una letra y un número");
    }
}
