package com.thropic.talki.identity.infrastructure.persistence;

import com.thropic.talki.identity.application.ports.PasswordResetTokenRepository;
import com.thropic.talki.identity.domain.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link PasswordResetTokenRepository}
 * (patrón JpaRefreshTokenRepository).
 */
public interface JpaPasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long>, PasswordResetTokenRepository {

    @Override
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Override
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :usedAt "
            + "where t.userId = :userId and t.usedAt is null")
    void invalidateActiveByUserId(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);
}
