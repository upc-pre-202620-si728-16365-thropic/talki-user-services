package com.thropic.talki.identity.application.services;

import com.thropic.talki.identity.domain.exception.InvalidCredentialsException;
import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.domain.model.RefreshToken;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
import com.thropic.talki.identity.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private com.thropic.talki.identity.application.events.UserRegisteredEventPublisher userRegisteredEventPublisher;

    @Mock
    private com.thropic.talki.identity.application.services.LoginAttemptService loginAttemptService;

    @Mock
    private com.thropic.talki.identity.application.ports.RefreshTokenRepository refreshTokenRepository;

    @Mock
    private com.thropic.talki.identity.infrastructure.security.TokenDenylist tokenDenylist;

    private AuthService authService;

    private static final long REFRESH_TTL_DAYS = 7;

    private AppUser sampleUser;

    private static final String INVALID_CREDENTIALS_MSG = "Credenciales inválidas";

    @BeforeEach
    void setUp() {
        sampleUser = new AppUser("manuel@upc.edu.pe", "hashed_password", "ManuelTumi", "ciclos_6_10");
        // ID lo asigna JPA en producción al persistir; en unit test lo seteamos
        // por reflection para que AuthService.login() pueda generar el JWT subject.
        ReflectionTestUtils.setField(sampleUser, "id", 1L);
        // Constructor manual: @InjectMocks no maneja el parámetro primitivo del TTL.
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider,
                userRegisteredEventPublisher, loginAttemptService, refreshTokenRepository,
                tokenDenylist, REFRESH_TTL_DAYS);
    }

    @Test
    void register_whenEmailIsNew_shouldSaveAndReturnUser() {
        when(userRepository.existsByEmail("manuel@upc.edu.pe")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(userRepository.save(any(AppUser.class))).thenReturn(sampleUser);

        AppUser result = authService.register("manuel@upc.edu.pe", "password123", "ManuelTumi", "ciclos_6_10");

        assertThat(result.getEmail()).isEqualTo("manuel@upc.edu.pe");
        assertThat(result.getUsername()).isEqualTo("ManuelTumi");
        verify(userRepository).save(any(AppUser.class));
        // Saga por coreografía: el registro debe publicar user.registered
        verify(userRegisteredEventPublisher).publish(
                org.mockito.ArgumentMatchers.argThat(e ->
                        e.getUserId().equals(1L)
                        && e.getEmail().equals("manuel@upc.edu.pe")
                        && e.getAcademicSegment().equals("ciclos_6_10")));
    }

    @Test
    void register_whenEmailAlreadyExists_shouldThrowConflict409() {
        when(userRepository.existsByEmail("manuel@upc.edu.pe")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.register("manuel@upc.edu.pe", "password123", "ManuelTumi", "ciclos_6_10")
        ).isInstanceOf(com.thropic.talki.identity.domain.exception.DuplicateEmailException.class)
         .hasMessageContaining("ya está registrado");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_whenCredentialsAreValid_shouldReturnBothTokens() {
        when(userRepository.findByEmail("manuel@upc.edu.pe")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtTokenProvider.generateToken(anyString(), anyString())).thenReturn("jwt-access-abc");

        AuthService.LoginResult result = authService.login("manuel@upc.edu.pe", "password123");

        assertThat(result.accessToken()).isEqualTo("jwt-access-abc");
        assertThat(result.refreshToken()).isNotBlank();
        // Se persiste solo el hash SHA-256, nunca el valor crudo.
        verify(refreshTokenRepository).save(argThat(t ->
                t.getUserId().equals(1L)
                && !t.getTokenHash().equals(result.refreshToken())
                && !t.isRevoked()));
    }

    @Test
    void login_whenUserNotFound_shouldGiveSameGenericMessageAsWrongPassword() {
        when(userRepository.findByEmail("noexiste@upc.edu.pe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("noexiste@upc.edu.pe", "password123"))
                .isInstanceOf(com.thropic.talki.identity.domain.exception.InvalidCredentialsException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void login_whenPasswordIsWrong_shouldGiveSameGenericMessageAsUnknownUser() {
        when(userRepository.findByEmail("manuel@upc.edu.pe")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("manuel@upc.edu.pe", "wrong_password"))
                .isInstanceOf(com.thropic.talki.identity.domain.exception.InvalidCredentialsException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void register_whenDuplicateEmailEvenForDemoSeed_shouldThrowDuplicateEmailException() {
        // El backdoor DEMO_EMAIL fue eliminado (SB1-07-D): ningún email escapa al 409.
        when(userRepository.existsByEmail("alejo.demo.av4@upc.edu.pe")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                "alejo.demo.av4@upc.edu.pe", "Demo1234!", "alejo_demo", "ciclos_6_10"))
                .isInstanceOf(com.thropic.talki.identity.domain.exception.DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
        verify(userRegisteredEventPublisher, never()).publish(any());
    }

    // ---------- US28: refresh con rotación y logout ----------

    private RefreshToken activeRefresh(String rawHash) {
        return new RefreshToken(1L, rawHash, Instant.now().plus(Duration.ofDays(7)));
    }

    @Test
    void refresh_whenTokenIsValid_shouldRotateAndReturnNewPair() {
        RefreshToken stored = activeRefresh(sha256Of("raw-opaco"));
        when(refreshTokenRepository.findByTokenHash(sha256Of("raw-opaco"))).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(jwtTokenProvider.generateToken(anyString(), anyString())).thenReturn("jwt-nuevo");

        AuthService.LoginResult result = authService.refresh("raw-opaco");

        assertThat(result.accessToken()).isEqualTo("jwt-nuevo");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(stored.isRevoked()).isTrue();
        assertThat(stored.getReplacedByHash()).isEqualTo(sha256Of(result.refreshToken()));
    }

    @Test
    void refresh_whenTokenIsUnknown_shouldThrow401() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("desconocido"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(INVALID_CREDENTIALS_MSG);
    }

    @Test
    void refresh_whenTokenIsRevoked_shouldThrow401() {
        RefreshToken revocado = activeRefresh(sha256Of("raw-revocado"));
        revocado.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(sha256Of("raw-revocado"))).thenReturn(Optional.of(revocado));

        assertThatThrownBy(() -> authService.refresh("raw-revocado"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refresh_whenTokenIsExpired_shouldThrow401() {
        RefreshToken vencido = new RefreshToken(1L, sha256Of("raw-vencido"),
                Instant.now().minus(Duration.ofHours(1)));
        when(refreshTokenRepository.findByTokenHash(sha256Of("raw-vencido"))).thenReturn(Optional.of(vencido));

        assertThatThrownBy(() -> authService.refresh("raw-vencido"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_whenRotatedTokenIsReused_shouldRevokeFamilyAndThrow() {
        RefreshToken rotado = activeRefresh(sha256Of("raw-reusado"));
        rotado.setRevoked(true);
        rotado.setReplacedByHash("hash-reemplazo");
        when(refreshTokenRepository.findByTokenHash(sha256Of("raw-reusado"))).thenReturn(Optional.of(rotado));

        assertThatThrownBy(() -> authService.refresh("raw-reusado"))
                .isInstanceOf(InvalidCredentialsException.class);

        // La familia entera (todos los activos del usuario) queda revocada.
        verify(refreshTokenRepository).revokeAllActiveByUserId(1L);
    }

    @Test
    void logout_shouldRevokeRefreshAndDenylistAccessToken() {
        RefreshToken stored = activeRefresh(sha256Of("raw-logout"));
        when(refreshTokenRepository.findByTokenHash(sha256Of("raw-logout"))).thenReturn(Optional.of(stored));
        when(jwtTokenProvider.validateToken("access-vigente")).thenReturn(true);
        when(jwtTokenProvider.getExpirationEpochMs("access-vigente")).thenReturn(System.currentTimeMillis() + 60_000);

        authService.logout("raw-logout", "access-vigente");

        assertThat(stored.isRevoked()).isTrue();
        verify(tokenDenylist).revoke(eq("access-vigente"), anyLong());
    }

    @Test
    void logout_shouldBeIdempotent_whenTokenUnknownOrRepeated() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> authService.logout("desconocido", null))
                .doesNotThrowAnyException();
        verify(refreshTokenRepository, never()).save(any());
    }

    private static String sha256Of(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void register_whenDuplicateEmailIsNotDemo_shouldThrowDuplicateEmailException() {
        when(userRepository.existsByEmail("otro@upc.edu.pe")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("otro@upc.edu.pe", "password123", "X", "ciclos_6_10"))
                .isInstanceOf(com.thropic.talki.identity.domain.exception.DuplicateEmailException.class);
        verify(userRegisteredEventPublisher, never()).publish(any());
    }

    @Test
    void register_whenAcademicSegmentProvided_shouldPersistIt() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        AppUser saved = new AppUser("a@b.com", "hash", "Gael", "ciclos_1_5");
        when(userRepository.save(any())).thenReturn(saved);

        AppUser result = authService.register("a@b.com", "password123", "Gael", "ciclos_1_5");

        assertThat(result.getAcademicSegment()).isEqualTo("ciclos_1_5");
    }

    @Test
    void register_whenPasswordTooShort_shouldThrowInvalidPassword() {
        assertThatThrownBy(() -> authService.register("a@b.com", "ab1", "Gael", "ciclos_6_10"))
                .isInstanceOf(com.thropic.talki.identity.domain.exception.InvalidPasswordException.class)
                .hasMessageContaining("8 caracteres");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_whenPasswordHasNoNumber_shouldThrowInvalidPassword() {
        assertThatThrownBy(() -> authService.register("a@b.com", "sololetras", "Gael", "ciclos_6_10"))
                .isInstanceOf(com.thropic.talki.identity.domain.exception.InvalidPasswordException.class);
    }

    @Test
    void register_whenPasswordHasNoLetter_shouldThrowInvalidPassword() {
        assertThatThrownBy(() -> authService.register("a@b.com", "12345678", "Gael", "ciclos_6_10"))
                .isInstanceOf(com.thropic.talki.identity.domain.exception.InvalidPasswordException.class);
    }

    @Test
    void register_whenAcademicSegmentIsUnknown_shouldThrowInvalidSegment() {
        assertThatThrownBy(() -> authService.register("a@b.com", "password123", "Gael", "posgrado"))
                .isInstanceOf(com.thropic.talki.identity.domain.exception.InvalidAcademicSegmentException.class)
                .hasMessageContaining("ciclos_1_5")
                .hasMessageContaining("ciclos_6_10");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_whenAcademicSegmentIsNull_shouldThrowInvalidSegment() {
        assertThatThrownBy(() -> authService.register("a@b.com", "password123", "Gael", null))
                .isInstanceOf(com.thropic.talki.identity.domain.exception.InvalidAcademicSegmentException.class);
    }
}
