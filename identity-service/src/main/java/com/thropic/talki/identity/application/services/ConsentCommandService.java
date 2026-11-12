package com.thropic.talki.identity.application.services;

import com.thropic.talki.identity.application.events.ConsentEventPublisher;
import com.thropic.talki.identity.application.ports.VoiceConsentRepository;
import com.thropic.talki.identity.domain.event.VoiceConsentEvent;
import com.thropic.talki.identity.domain.exception.ConsentNotFoundException;
import com.thropic.talki.identity.domain.exception.DuplicateConsentException;
import com.thropic.talki.identity.domain.exception.InvalidConsentPolicyException;
import com.thropic.talki.identity.domain.model.VoiceConsent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de consentimiento de voz (US33). El repositorio conserva el
 * historial: aceptar una versión distinta o re-aceptar sobre una retirada
 * crea una fila nueva; retirar solo marca withdrawnAt.
 */
@Service
@Transactional
public class ConsentCommandService {

    private final VoiceConsentRepository voiceConsentRepository;
    private final ConsentEventPublisher consentEventPublisher;

    public ConsentCommandService(VoiceConsentRepository voiceConsentRepository,
                                 ConsentEventPublisher consentEventPublisher) {
        this.voiceConsentRepository = voiceConsentRepository;
        this.consentEventPublisher = consentEventPublisher;
    }

    public VoiceConsent accept(Long userId, String policyVersion) {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new InvalidConsentPolicyException();
        }
        boolean duplicated = voiceConsentRepository.findByUserId(userId).stream()
                .anyMatch(c -> c.getPolicyVersion().equals(policyVersion) && c.isActive(policyVersion));
        if (duplicated) {
            throw new DuplicateConsentException();
        }
        // Versión nueva, o re-aceptación sobre una retirada u otra versión: fila nueva.
        VoiceConsent consent = voiceConsentRepository.save(new VoiceConsent(userId, policyVersion));
        consentEventPublisher.publish(new VoiceConsentEvent(VoiceConsentEvent.TYPE_REGISTERED,
                new VoiceConsentEvent.VoiceConsentData(userId, consent.getPolicyVersion(),
                        consent.getScope(), consent.getId().toString())));
        return consent;
    }

    public VoiceConsent withdraw(Long userId, String consentId) {
        VoiceConsent consent = voiceConsentRepository.findByUserId(userId).stream()
                .filter(c -> c.getId().equals(parseId(consentId)))
                .findFirst()
                .orElseThrow(ConsentNotFoundException::new);
        Instant now = Instant.now();
        boolean alreadyWithdrawn = consent.getWithdrawnAt() != null;
        consent.withdraw(now);
        if (!alreadyWithdrawn) {
            // Solo la primera marca persiste y notifica; repetir es un no-op.
            voiceConsentRepository.save(consent);
            consentEventPublisher.publish(new VoiceConsentEvent(VoiceConsentEvent.TYPE_WITHDRAWN,
                    new VoiceConsentEvent.VoiceConsentData(userId, consent.getPolicyVersion(),
                            consent.getScope(), consent.getId().toString())));
        }
        return consent;
    }

    /** Historial completo del usuario: activos y retirados. */
    public List<VoiceConsent> status(Long userId) {
        return voiceConsentRepository.findByUserId(userId);
    }

    /** Compuerta US33: ¿hay consentimiento activo que cubra la versión requerida? */
    public boolean isActiveFor(Long userId, String requiredVersion) {
        return voiceConsentRepository.findByUserId(userId).stream()
                .anyMatch(c -> c.isActive(requiredVersion));
    }

    /** Un id malformado se trata como consentimiento inexistente (sin 500). */
    private UUID parseId(String consentId) {
        try {
            return UUID.fromString(consentId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ConsentNotFoundException();
        }
    }
}
