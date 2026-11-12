package com.thropic.talki.identity.application.ports;

import com.thropic.talki.identity.domain.model.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de persistencia de tokens de recuperación de contraseña. La capa
 * de aplicación no conoce JPA; el adaptador vive en infrastructure.persistence.
 */
public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Marca como usados todos los tokens activos del usuario (un solo activo). */
    void invalidateActiveByUserId(Long userId, Instant usedAt);
}
