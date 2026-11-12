package com.thropic.talki.identity.infrastructure.persistence;

import com.thropic.talki.identity.application.ports.VoiceConsentRepository;
import com.thropic.talki.identity.domain.model.VoiceConsent;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Adaptador JPA del puerto {@link VoiceConsentRepository} (US33).
 * Delega en el SPI de Spring Data; el historial se conserva íntegro
 * porque el retiro solo actualiza {@code withdrawn_at}.
 */
@Repository
public class JpaVoiceConsentRepositoryAdapter implements VoiceConsentRepository {

    private final JpaVoiceConsentRepository jpaRepository;

    public JpaVoiceConsentRepositoryAdapter(JpaVoiceConsentRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<VoiceConsent> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public VoiceConsent save(VoiceConsent consent) {
        return jpaRepository.save(consent);
    }
}
