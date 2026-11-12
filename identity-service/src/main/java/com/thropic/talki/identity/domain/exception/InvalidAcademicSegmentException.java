package com.thropic.talki.identity.domain.exception;

/** Segmento académico fuera del catálogo permitido (HTTP 400). */
public class InvalidAcademicSegmentException extends RuntimeException {

    public InvalidAcademicSegmentException() {
        super("El segmento académico solo admite los valores ciclos_1_5 o ciclos_6_10");
    }
}
