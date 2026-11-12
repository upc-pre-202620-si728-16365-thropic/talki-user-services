package com.thropic.talki.identity.domain.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Token de un solo uso para recuperar la contraseña. Solo se persiste el
 * hash SHA-256; el valor crudo viaja exclusivamente en el evento de entrega.
 * Vigencia 30 minutos. Un solo token activo por usuario: emitir uno nuevo
 * marca usedAt en los anteriores.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(unique = true, nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Null mientras el token está activo; fecha de consumo si ya se usó. */
    private Instant usedAt;

    public PasswordResetToken() {}

    public PasswordResetToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public boolean isUsed() { return usedAt != null; }
    public void markUsed(Instant at) { this.usedAt = at; }
}
