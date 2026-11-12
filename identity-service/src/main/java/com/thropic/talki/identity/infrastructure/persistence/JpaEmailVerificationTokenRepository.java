package com.thropic.talki.identity.infrastructure.persistence;

import com.thropic.talki.identity.application.ports.EmailVerificationTokenRepository;
import com.thropic.talki.identity.domain.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link EmailVerificationTokenRepository}
 * (patrón JpaRefreshTokenRepository).
 */
public interface JpaEmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long>, EmailVerificationTokenRepository {

    @Override
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Override
    @Modifying
    @Query("update EmailVerificationToken t set t.usedAt = :usedAt "
            + "where t.userId = :userId and t.usedAt is null")
    void invalidateActiveByUserId(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);
}
