package com.thropic.talki.identity.infrastructure.persistence;

import com.thropic.talki.identity.application.ports.RefreshTokenRepository;
import com.thropic.talki.identity.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link RefreshTokenRepository}.
 */
public interface JpaRefreshTokenRepository extends JpaRepository<RefreshToken, Long>, RefreshTokenRepository {

    @Override
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Override
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.userId = :userId and t.revoked = false")
    void revokeAllActiveByUserId(@Param("userId") Long userId);
}
