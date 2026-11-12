package com.thropic.talki.identity.infrastructure.persistence;

import com.thropic.talki.identity.domain.model.VoiceConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** SPI de Spring Data para la entidad VoiceConsent (US33). */
public interface JpaVoiceConsentRepository extends JpaRepository<VoiceConsent, UUID> {

    List<VoiceConsent> findByUserId(Long userId);
}
