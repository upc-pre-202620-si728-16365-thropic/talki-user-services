package com.thropic.talki.identity.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Consentimiento de procesamiento de voz (US33, informe TB2 §5.8.1).
 * El repositorio conserva HISTORIAL: una fila por aceptación; retirar
 * solo marca {@code withdrawnAt}, nunca borra la fila.
 */
@Entity
@Table(name = "voice_consents")
public class VoiceConsent {

    /** Alcance fijo en este sprint: no es configuración. */
    public static final String SCOPE_VOICE_PROCESSING = "voice_processing";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @Column(nullable = false)
    private String scope;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    public VoiceConsent() {}

    public VoiceConsent(Long userId, String policyVersion) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.policyVersion = policyVersion;
        this.scope = SCOPE_VOICE_PROCESSING;
        this.acceptedAt = Instant.now();
    }

    /**
     * US33: el consentimiento está activo si no fue retirado y su versión
     * de política cubre la requerida por el procesamiento.
     */
    public boolean isActive(String requiredVersion) {
        return withdrawnAt == null && covers(requiredVersion);
    }

    /** Retiro idempotente: la primera marca gana; repetir no cambia la fecha. */
    public void withdraw(Instant now) {
        if (withdrawnAt == null) {
            withdrawnAt = now;
        }
    }

    /** v2 cubre v1; versiones no numéricas solo cubren a su igual exacto. */
    private boolean covers(String requiredVersion) {
        Integer have = majorVersion(policyVersion);
        Integer need = majorVersion(requiredVersion);
        return have != null && need != null ? have >= need : policyVersion.equals(requiredVersion);
    }

    private Integer majorVersion(String version) {
        if (version == null) {
            return null;
        }
        try {
            return Integer.valueOf(version.trim().replaceFirst("^v", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPolicyVersion() { return policyVersion; }
    public String getScope() { return scope; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
}
