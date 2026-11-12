package com.thropic.talki.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de consentimiento de voz (US33) publicado sobre talki.events.
 * CONTRATO PROPUESTO (pendiente de ratificación): las routing keys
 * voice.consent.registered y voice.consent.withdrawn aún no están fijadas
 * en el informe; el payload no incluye email ni PII.
 */
public class VoiceConsentEvent {

    /** Routing keys propuestas = eventType. */
    public static final String TYPE_REGISTERED = "voice.consent.registered";
    public static final String TYPE_WITHDRAWN = "voice.consent.withdrawn";

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private Long userId;
    private String policyVersion;
    private String scope;
    private String consentId;

    public VoiceConsentEvent() {}

    public VoiceConsentEvent(String eventType, VoiceConsentData data) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.occurredAt = Instant.now();
        this.userId = data.userId();
        this.policyVersion = data.policyVersion();
        this.scope = data.scope();
        this.consentId = data.consentId();
    }

    /** Datos mínimos del consentimiento que viajan en el evento. */
    public record VoiceConsentData(Long userId, String policyVersion, String scope, String consentId) {}

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
