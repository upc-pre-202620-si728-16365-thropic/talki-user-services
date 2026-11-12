package com.thropic.talki.notification.domain.event;

import java.time.Instant;

/**
 * Eventos del ciclo de vida de la cuenta publicados por identity-service
 * sobre talki.events (CONTRATO PROPUESTO pendiente de ratificación):
 * user.email-verification-requested, user.password-reset-requested y
 * user.profile-updated comparten esta forma; los campos que no aplican
 * viajan null (p. ej. profile-updated no lleva token).
 */
public class UserAccountEvent {

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private Long userId;
    private String email;
    private String token;
    private String username;
    private String academicSegment;

    public UserAccountEvent() {}

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
