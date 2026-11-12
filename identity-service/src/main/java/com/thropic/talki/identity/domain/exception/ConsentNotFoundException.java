package com.thropic.talki.identity.domain.exception;

/** El consentimiento no existe o no pertenece al usuario autenticado (HTTP 404). */
public class ConsentNotFoundException extends RuntimeException {

    public ConsentNotFoundException() {
        super("Consentimiento de voz no encontrado para el usuario");
    }
}
