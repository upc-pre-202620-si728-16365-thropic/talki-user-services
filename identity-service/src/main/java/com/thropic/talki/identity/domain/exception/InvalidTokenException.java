package com.thropic.talki.identity.domain.exception;

/**
 * Token de un solo uso inválido, expirado o ya utilizado (HTTP 400).
 * Mensaje único y genérico: no distingue cuál de las tres causas fue
 * (evita sondear el estado de los tokens).
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("El token es inválido, expiró o ya fue utilizado");
    }
}
