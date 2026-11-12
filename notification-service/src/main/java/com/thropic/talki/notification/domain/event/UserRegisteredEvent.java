package com.thropic.talki.notification.domain.event;

import java.time.Instant;

/**
 * user.registered publicado por identity-service sobre talki.events.
 * Ojo: el publisher aún no setea eventId, por lo que la deduplicación
 * no aplica a este evento hasta que identity lo incluya.
 */
public class UserRegisteredEvent {

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private Long userId;
    private String email;
    private String username;
    private String academicSegment;

    public UserRegisteredEvent() {}

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
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAcademicSegment() { return academicSegment; }
    public void setAcademicSegment(String academicSegment) { this.academicSegment = academicSegment; }
}
