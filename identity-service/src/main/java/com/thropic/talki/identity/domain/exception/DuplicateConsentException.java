package com.thropic.talki.identity.domain.exception;

/** Ya existe un consentimiento activo para la misma versión de política (HTTP 409). */
public class DuplicateConsentException extends RuntimeException {

    public DuplicateConsentException() {
        super("Ya existe un consentimiento de voz activo para esta versión de política");
    }
}
