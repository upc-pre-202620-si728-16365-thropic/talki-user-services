package com.thropic.talki.identity.application.services;

import com.thropic.talki.identity.application.events.AccountEventPublisher;
import com.thropic.talki.identity.application.ports.EmailVerificationTokenRepository;
import com.thropic.talki.identity.domain.event.UserAccountEvent;
import com.thropic.talki.identity.domain.exception.InvalidCredentialsException;
import com.thropic.talki.identity.domain.exception.InvalidTokenException;
import com.thropic.talki.identity.domain.exception.TooManyLoginAttemptsException;
import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.domain.model.EmailVerificationToken;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
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
    private final AccountEventPublisher accountEventPublisher;

    public AccountService(AppUserRepository userRepository,
                          EmailVerificationTokenRepository emailVerificationTokens,
                          AccountEventPublisher accountEventPublisher) {
        this.userRepository = userRepository;
        this.emailVerificationTokens = emailVerificationTokens;
        this.accountEventPublisher = accountEventPublisher;
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
