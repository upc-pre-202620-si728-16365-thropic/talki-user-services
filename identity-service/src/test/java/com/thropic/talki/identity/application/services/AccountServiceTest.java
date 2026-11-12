package com.thropic.talki.identity.application.services;

import com.thropic.talki.identity.application.events.AccountEventPublisher;
import com.thropic.talki.identity.application.ports.EmailVerificationTokenRepository;
import com.thropic.talki.identity.application.ports.PasswordResetTokenRepository;
import com.thropic.talki.identity.application.ports.RefreshTokenRepository;
import com.thropic.talki.identity.domain.event.UserAccountEvent;
import com.thropic.talki.identity.domain.exception.InvalidAcademicSegmentException;
import com.thropic.talki.identity.domain.exception.InvalidPasswordException;
import com.thropic.talki.identity.domain.exception.InvalidTokenException;
import com.thropic.talki.identity.domain.exception.TooManyLoginAttemptsException;
import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.domain.model.EmailVerificationToken;
import com.thropic.talki.identity.domain.model.PasswordResetToken;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokens;

    @Mock
    private PasswordResetTokenRepository passwordResetTokens;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccountEventPublisher accountEventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountService accountService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(userRepository, emailVerificationTokens,
                passwordResetTokens, refreshTokenRepository, accountEventPublisher, passwordEncoder);
        user = new AppUser("manuel@upc.edu.pe", "hash_actual", "ManuelTumi", "ciclos_6_10");
        // ID lo asigna JPA en producción; en unit test por reflection (patrón AuthServiceTest).
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    private EmailVerificationToken activeEmailToken(String hash) {
        return new EmailVerificationToken(1L, hash, Instant.now().plusSeconds(3600));
    }

    private PasswordResetToken activeResetToken(String hash) {
        return new PasswordResetToken(1L, hash, Instant.now().plusSeconds(1800));
    }

    // ---------- Verificación: emisión y consumo single-use ----------

    @Test
    void issueEmailVerification_shouldInvalidatePreviousAndPublishRawTokenOnlyInEvent() {
        accountService.issueEmailVerification(user);

        // Un solo activo: el token previo del usuario queda marcado usado.
        verify(emailVerificationTokens).invalidateActiveByUserId(eq(1L), any(Instant.class));
        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokens).save(saved.capture());
        ArgumentCaptor<UserAccountEvent> event = ArgumentCaptor.forClass(UserAccountEvent.class);
        verify(accountEventPublisher).publish(event.capture());

        // El crudo solo viaja en el evento; en BD únicamente el hash SHA-256.
        String raw = event.getValue().getToken();
        assertThat(raw).isNotBlank();
        assertThat(saved.getValue().getTokenHash()).isEqualTo(hashes(raw));
        assertThat(event.getValue().getEventType()).isEqualTo(UserAccountEvent.TYPE_EMAIL_VERIFICATION_REQUESTED);
        assertThat(event.getValue().getEmail()).isEqualTo("manuel@upc.edu.pe");
    }

    private static String hashes(String raw) {
        return AuthService.sha256Hex(raw);
    }

    @Test
    void verifyEmail_happyPath_shouldMarkUsedAndSetEmailVerified() {
        EmailVerificationToken token = activeEmailToken(AuthService.sha256Hex("raw-1"));
        when(emailVerificationTokens.findByTokenHash(AuthService.sha256Hex("raw-1"))).thenReturn(Optional.of(token));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        accountService.verifyEmail("raw-1");

        assertThat(token.isUsed()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        verify(emailVerificationTokens).save(token);
    }

    @Test
    void verifyEmail_whenTokenAlreadyUsed_shouldReturnGeneric400() {
        EmailVerificationToken token = activeEmailToken(AuthService.sha256Hex("raw-1"));
        token.markUsed(Instant.now());
        when(emailVerificationTokens.findByTokenHash(AuthService.sha256Hex("raw-1"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> accountService.verifyEmail("raw-1"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("El token es inválido, expiró o ya fue utilizado");
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmail_whenTokenExpired_shouldReturnGeneric400() {
        EmailVerificationToken token = new EmailVerificationToken(1L, AuthService.sha256Hex("raw-1"), Instant.now().minusSeconds(1));
        when(emailVerificationTokens.findByTokenHash(AuthService.sha256Hex("raw-1"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> accountService.verifyEmail("raw-1"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyEmail_whenTokenUnknown_shouldReturnGeneric400() {
        when(emailVerificationTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.verifyEmail("desconocido"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void resendVerification_fourthCallInWindow_shouldReturn429() {
        when(userRepository.findByEmail("manuel@upc.edu.pe")).thenReturn(Optional.of(user));

        accountService.resendVerification("manuel@upc.edu.pe");
        accountService.resendVerification("manuel@upc.edu.pe");
        accountService.resendVerification("manuel@upc.edu.pe");
        // Cada emisión invalida el activo previo (un solo token activo por usuario).
        verify(emailVerificationTokens, times(3)).invalidateActiveByUserId(eq(1L), any(Instant.class));

        assertThatThrownBy(() -> accountService.resendVerification("manuel@upc.edu.pe"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        verify(accountEventPublisher, times(3)).publish(any(UserAccountEvent.class));
    }

    // ---------- Recuperación: solicitud y reset ----------

    @Test
    void requestPasswordReset_whenEmailUnknown_shouldNeverPublishOrPersist() {
        when(userRepository.findByEmail("fantasma@upc.edu.pe")).thenReturn(Optional.empty());

        accountService.requestPasswordReset("fantasma@upc.edu.pe");

        verify(accountEventPublisher, never()).publish(any());
        verify(passwordResetTokens, never()).save(any());
    }

    @Test
    void requestPasswordReset_whenEmailExists_shouldInvalidatePreviousAndPublishRawToken() {
        when(userRepository.findByEmail("manuel@upc.edu.pe")).thenReturn(Optional.of(user));

        accountService.requestPasswordReset("manuel@upc.edu.pe");

        verify(passwordResetTokens).invalidateActiveByUserId(eq(1L), any(Instant.class));
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokens).save(saved.capture());
        ArgumentCaptor<UserAccountEvent> event = ArgumentCaptor.forClass(UserAccountEvent.class);
        verify(accountEventPublisher).publish(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(UserAccountEvent.TYPE_PASSWORD_RESET_REQUESTED);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(hashes(event.getValue().getToken()));
    }

    @Test
    void resetPassword_happyPath_shouldUpdateHashAndRevokeWholeRefreshFamily() {
        PasswordResetToken token = activeResetToken(AuthService.sha256Hex("raw-1"));
        when(passwordResetTokens.findByTokenHash(AuthService.sha256Hex("raw-1"))).thenReturn(Optional.of(token));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword456")).thenReturn("hash_nuevo");

        accountService.resetPassword("raw-1", "NewPassword456");

        assertThat(token.isUsed()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("hash_nuevo");
        // El reset revoca TODA la familia de refresh tokens activos.
        verify(refreshTokenRepository).revokeAllActiveByUserId(1L);
    }

    @Test
    void resetPassword_withWeakPassword_shouldReturnPasswordRule400() {
        assertThatThrownBy(() -> accountService.resetPassword("raw-1", "corta"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("La contraseña debe tener al menos 8 caracteres e incluir al menos una letra y un número");
        verify(passwordResetTokens, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(anyLong());
    }

    @Test
    void resetPassword_whenTokenReused_shouldReturnGeneric400() {
        PasswordResetToken token = activeResetToken(AuthService.sha256Hex("raw-1"));
        token.markUsed(Instant.now());
        when(passwordResetTokens.findByTokenHash(AuthService.sha256Hex("raw-1"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> accountService.resetPassword("raw-1", "NewPassword456"))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(anyLong());
    }

    @Test
    void resetPassword_whenTokenExpired_shouldReturnGeneric400() {
        PasswordResetToken token = new PasswordResetToken(1L, AuthService.sha256Hex("raw-1"), Instant.now().minusSeconds(1));
        when(passwordResetTokens.findByTokenHash(AuthService.sha256Hex("raw-1"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> accountService.resetPassword("raw-1", "NewPassword456"))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ---------- PATCH de perfil ----------

    @Test
    void updateProfile_happyPath_shouldPersistChangesAndPublishOnlyNewValues() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AppUser updated = accountService.updateProfile(1L, "NuevoNombre", "ciclos_1_5");

        assertThat(updated.getUsername()).isEqualTo("NuevoNombre");
        assertThat(updated.getAcademicSegment()).isEqualTo("ciclos_1_5");
        assertThat(updated.getEmail()).isEqualTo("manuel@upc.edu.pe"); // email intacto
        ArgumentCaptor<UserAccountEvent> event = ArgumentCaptor.forClass(UserAccountEvent.class);
        verify(accountEventPublisher).publish(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(UserAccountEvent.TYPE_PROFILE_UPDATED);
        assertThat(event.getValue().getUsername()).isEqualTo("NuevoNombre");
        assertThat(event.getValue().getAcademicSegment()).isEqualTo("ciclos_1_5");
        assertThat(event.getValue().getToken()).isNull();
    }

    @Test
    void updateProfile_withUnknownSegment_shouldReturnCatalog400() {
        assertThatThrownBy(() -> accountService.updateProfile(1L, "Nombre", "posgrado"))
                .isInstanceOf(InvalidAcademicSegmentException.class)
                .hasMessage("El segmento académico solo admite los valores ciclos_1_5 o ciclos_6_10");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_withBlankFields_shouldNotPublish() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        accountService.updateProfile(1L, "  ", null);

        verify(userRepository, never()).save(any());
        verify(accountEventPublisher, never()).publish(any());
    }

    @Test
    void forgotPassword_rateLimit_countsPerEmail_insideWindow() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        for (int i = 0; i < 5; i++) {
            accountService.requestPasswordReset("muchas@upc.edu.pe");
        }
        assertThatThrownBy(() -> accountService.requestPasswordReset("muchas@upc.edu.pe"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        verify(accountEventPublisher, never()).publish(any(UserAccountEvent.class));
    }
}
