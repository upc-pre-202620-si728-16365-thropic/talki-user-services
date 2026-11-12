package com.thropic.talki.identity.application.ports;

import com.thropic.talki.identity.domain.model.RefreshToken;

import java.util.Optional;

/**
 * Puerto de persistencia de refresh tokens (US28). La capa de aplicación no
 * conoce JPA; el adaptador vive en infrastructure.persistence.
 */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoca todos los refresh tokens activos del usuario (reuso detectado). */
    void revokeAllActiveByUserId(Long userId);
}
