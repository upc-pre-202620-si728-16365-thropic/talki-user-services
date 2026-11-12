package com.thropic.talki.notification.domain.event;

import java.time.Instant;

/**
 * voice.consent.withdrawn publicado por identity-service sobre talki.events
 * (US33). Routing key PROPUESTA pendiente de ratificación en el informe.
 */
public class VoiceConsentWithdrawnEvent {

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private Long userId;
    private String policyVersion;
    private String scope;
    private String consentId;

    public VoiceConsentWithdrawnEvent() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getConsentId() { return consentId; }
    public void setConsentId(String consentId) { this.consentId = consentId; }
}
