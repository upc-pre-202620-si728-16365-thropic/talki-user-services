package com.thropic.talki.identity.application.services;

import com.thropic.talki.identity.application.events.AccountEventPublisher;
import com.thropic.talki.identity.application.ports.EmailVerificationTokenRepository;
import com.thropic.talki.identity.application.ports.PasswordResetTokenRepository;
import com.thropic.talki.identity.application.ports.RefreshTokenRepository;
import com.thropic.talki.identity.domain.event.UserAccountEvent;
import com.thropic.talki.identity.domain.exception.InvalidCredentialsException;
import com.thropic.talki.identity.domain.exception.InvalidPasswordException;
import com.thropic.talki.identity.domain.exception.InvalidTokenException;
import com.thropic.talki.identity.domain.exception.TooManyLoginAttemptsException;
import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.domain.model.EmailVerificationToken;
import com.thropic.talki.identity.domain.model.PasswordResetToken;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Casos de uso del ciclo de vida de la cuenta (sprint account-lifecycle):
 * verificación de correo, recuperación de contraseña y edición de perfil.
 * Los tokens son de un solo uso: en BD solo el hash SHA-256 con expiresAt y
 * usedAt; el valor crudo viaja únicamente en el evento de entrega. Un solo
 * token activo por usuario: emitir uno nuevo invalida los anteriores.
 */
@Service
@Transactional
public class AccountService {

    private static final Duration EMAIL_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    /** Límite de reenvíos de verificación: 3 por email en 15 minutos. */
    private final LoginAttemptService resendLimiter = new LoginAttemptService(3, Duration.ofMinutes(15));
    /** Límite de solicitudes de recuperación: 5 por email en 15 minutos. */
    private final LoginAttemptService forgotLimiter = new LoginAttemptService(5, Duration.ofMinutes(15));

    private final AppUserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokens;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountEventPublisher accountEventPublisher;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AppUserRepository userRepository,
                          EmailVerificationTokenRepository emailVerificationTokens,
                          PasswordResetTokenRepository passwordResetTokens,
                          RefreshTokenRepository refreshTokenRepository,
                          AccountEventPublisher accountEventPublisher,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emailVerificationTokens = emailVerificationTokens;
        this.passwordResetTokens = passwordResetTokens;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accountEventPublisher = accountEventPublisher;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Emite el token de verificación del usuario: invalida el activo previo,
     * persiste el hash y publica el evento con el valor crudo. Lo llama
     * AuthService.register (cuenta nace no verificada) y resendVerification.
     */
    public void issueEmailVerification(AppUser user) {
        emailVerificationTokens.invalidateActiveByUserId(user.getId(), Instant.now());
        String raw = AuthService.generateOpaqueToken();
        emailVerificationTokens.save(new EmailVerificationToken(
                user.getId(), AuthService.sha256Hex(raw), Instant.now().plus(EMAIL_TOKEN_TTL)));
        accountEventPublisher.publish(new UserAccountEvent(
                UserAccountEvent.TYPE_EMAIL_VERIFICATION_REQUESTED,
                user.getId(), user.getEmail(), raw, null, null));
    }

    /** Consume el token y verifica el correo. Inválido/vencido/usado → 400 genérico. */
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = findUsable(
                rawToken == null ? null : emailVerificationTokens.findByTokenHash(AuthService.sha256Hex(rawToken)).orElse(null));
        token.markUsed(Instant.now());
        emailVerificationTokens.save(token);
        userRepository.findById(token.getUserId()).ifPresent(user -> user.setEmailVerified(true));
    }

    /** Reenvía la verificación del usuario autenticado: 3 por email en 15 minutos. */
    public void resendVerification(String email) {
        assertAllowed(resendLimiter, email);
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        issueEmailVerification(user);
    }

    /**
     * Solicita la recuperación: 5 por email en 15 minutos. Anti-enumeración:
     * el email desconocido no publica nada; la respuesta HTTP idéntica la
     * construye el controlador.
     */
    public void requestPasswordReset(String email) {
        assertAllowed(forgotLimiter, email);
        forgotLimiter.recordFailure(email); // toda solicitud cuenta dentro de la ventana
        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetTokens.invalidateActiveByUserId(user.getId(), Instant.now());
            String raw = AuthService.generateOpaqueToken();
            passwordResetTokens.save(new PasswordResetToken(
                    user.getId(), AuthService.sha256Hex(raw), Instant.now().plus(RESET_TOKEN_TTL)));
            accountEventPublisher.publish(new UserAccountEvent(
                    UserAccountEvent.TYPE_PASSWORD_RESET_REQUESTED,
                    user.getId(), user.getEmail(), raw, null, null));
        });
    }

    /**
     * Consume el token de un solo uso y cambia la contraseña (misma PASSWORD_RULE
     * que register). Revoca TODA la familia de refresh tokens activos del usuario;
     * los access tokens en curso siguen válidos hasta su exp (15 min, denylist actual).
     */
    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword == null || !AuthService.PASSWORD_RULE.matcher(newPassword).matches()) {
            throw new InvalidPasswordException();
        }
        PasswordResetToken token = rawToken == null ? null
                : passwordResetTokens.findByTokenHash(AuthService.sha256Hex(rawToken)).orElse(null);
        if (token == null || token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException();
        }
        token.markUsed(Instant.now());
        passwordResetTokens.save(token);
        AppUser user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidTokenException::new);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllActiveByUserId(user.getId());
    }

    private EmailVerificationToken findUsable(EmailVerificationToken token) {
        if (token == null || token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException();
        }
        return token;
    }

    private void assertAllowed(LoginAttemptService limiter, String email) {
        try {
            limiter.assertAllowed(email);
        } catch (TooManyLoginAttemptsException e) {
            throw e; // 429 con Retry-After, mismo handler que login
        }
    }
}
