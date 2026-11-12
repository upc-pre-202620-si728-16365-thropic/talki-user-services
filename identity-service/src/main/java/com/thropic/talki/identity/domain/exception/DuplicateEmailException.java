package com.thropic.talki.identity.domain.exception;

/** El email ya está registrado (HTTP 409). */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("El email ya está registrado");
    }
}
