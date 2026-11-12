package com.thropic.talki.identity.domain.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Eventos del ciclo de vida de la cuenta publicados sobre talki.events.
 * CONTRATO PROPUESTO (pendiente de ratificación en el informe): las routing
 * keys user.email-verification-requested, user.password-reset-requested y
 * user.profile-updated aún no están fijadas. El token crudo viaja SOLO en el
 * evento (entrega); en BD se persiste únicamente su hash SHA-256.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserAccountEvent {

    /** Routing keys propuestas = eventType. */
    public static final String TYPE_EMAIL_VERIFICATION_REQUESTED = "user.email-verification-requested";
    public static final String TYPE_PASSWORD_RESET_REQUESTED = "user.password-reset-requested";
    public static final String TYPE_PROFILE_UPDATED = "user.profile-updated";

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private Long userId;
    private String email;
    /** Valor crudo del token de un solo uso; null en profile-updated. */
    private String token;
    /** Solo en profile-updated: valores nuevos. */
    private String username;
    private String academicSegment;

    public UserAccountEvent() {}

    public UserAccountEvent(String eventType, Long userId, String email,
                            String token, String username, String academicSegment) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.occurredAt = Instant.now();
        this.userId = userId;
        this.email = email;
        this.token = token;
        this.username = username;
        this.academicSegment = academicSegment;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAcademicSegment() { return academicSegment; }
    public void setAcademicSegment(String academicSegment) { this.academicSegment = academicSegment; }
}
