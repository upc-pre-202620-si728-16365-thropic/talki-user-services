package com.thropic.talki.identity.application.ports;

import com.thropic.talki.identity.domain.model.VoiceConsent;

import java.util.List;

/**
 * Puerto de persistencia de consentimientos de voz (US33). Conserva el
 * historial completo del usuario: retirar no elimina filas. La capa de
 * aplicación no conoce JPA; el adaptador vive en infrastructure.persistence.
 */
public interface VoiceConsentRepository {

    /** Historial del usuario: todas las aceptaciones (activas y retiradas). */
    List<VoiceConsent> findByUserId(Long userId);

    VoiceConsent save(VoiceConsent consent);
}
