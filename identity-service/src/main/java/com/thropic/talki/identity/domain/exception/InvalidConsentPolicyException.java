package com.thropic.talki.identity.domain.exception;

/** La versión de política enviada está vacía o ausente (HTTP 400). */
public class InvalidConsentPolicyException extends RuntimeException {

    public InvalidConsentPolicyException() {
        super("La versión de política de voz es obligatoria");
    }
}
