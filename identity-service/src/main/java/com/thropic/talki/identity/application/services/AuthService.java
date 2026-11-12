package com.thropic.talki.identity.application.services;

import com.thropic.talki.identity.application.events.UserRegisteredEventPublisher;
import com.thropic.talki.identity.application.ports.RefreshTokenRepository;
import com.thropic.talki.identity.domain.event.UserRegisteredEvent;
import com.thropic.talki.identity.domain.exception.DuplicateEmailException;
import com.thropic.talki.identity.domain.exception.InvalidAcademicSegmentException;
import com.thropic.talki.identity.domain.exception.InvalidCredentialsException;
import com.thropic.talki.identity.domain.exception.InvalidPasswordException;
import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.domain.model.RefreshToken;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
import com.thropic.talki.identity.infrastructure.security.JwtTokenProvider;
import com.thropic.talki.identity.infrastructure.security.TokenDenylist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Transactional
public class AuthService {

    /**
     * Regla de contraseña: mínimo 8 caracteres, al menos una letra y un número.
     * Package: la reutiliza AccountService en el reset con el mismo mensaje de error.
     */
    static final Pattern PASSWORD_RULE = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Catálogo de segmentos académicos. Package: lo reutiliza AccountService en PATCH. */
    static final Set<String> VALID_SEGMENTS = Set.of("ciclos_1_5", "ciclos_6_10");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRegisteredEventPublisher userRegisteredEventPublisher;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenDenylist tokenDenylist;
    private final AccountService accountService;
    private final long refreshTtlDays;

    /** Par de tokens devuelto por login y refresh (US28). */
    public record LoginResult(String accessToken, String refreshToken) {}

    public AuthService(AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       UserRegisteredEventPublisher userRegisteredEventPublisher,
                       LoginAttemptService loginAttemptService,
                       RefreshTokenRepository refreshTokenRepository,
                       TokenDenylist tokenDenylist,
                       AccountService accountService,
                       @Value("${jwt.refresh-ttl-days:7}") long refreshTtlDays) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRegisteredEventPublisher = userRegisteredEventPublisher;
        this.loginAttemptService = loginAttemptService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenDenylist = tokenDenylist;
        this.accountService = accountService;
        this.refreshTtlDays = refreshTtlDays;
    }

    public AppUser register(String email, String rawPassword, String username, String academicSegment) {
        if (rawPassword == null || !PASSWORD_RULE.matcher(rawPassword).matches()) {
            throw new InvalidPasswordException();
        }
        if (academicSegment == null || !VALID_SEGMENTS.contains(academicSegment)) {
            throw new InvalidAcademicSegmentException();
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        String hash = passwordEncoder.encode(rawPassword);
        AppUser user = new AppUser(email, hash, username, academicSegment);
        AppUser saved = userRepository.save(user);

        // Saga por coreografía: notifica a los bounded contexts downstream
        // (session-service, etc.) para que proyecten localmente al usuario.
        userRegisteredEventPublisher.publish(new UserRegisteredEvent(
                saved.getId(),
                saved.getEmail(),
                saved.getUsername(),
                saved.getAcademicSegment()
        ));

        // La cuenta nace NO verificada: emite el token y publica el evento de
        // verificación de correo (sprint account-lifecycle).
        accountService.issueEmailVerification(saved);
        return saved;
    }

    public LoginResult login(String email, String rawPassword) {
        loginAttemptService.assertAllowed(email);
        // Mensaje idéntico en ambas ramas: no se revela si falló el email o la contraseña.
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            loginAttemptService.recordFailure(email);
            throw new InvalidCredentialsException();
        }
        loginAttemptService.reset(email);
        String accessToken = jwtTokenProvider.generateToken(email, user.getId().toString());
        String refreshToken = issueRefreshToken(user.getId());
        return new LoginResult(accessToken, refreshToken);
    }

    /**
     * US28: valida el refresh token opaco y lo rota. Si se detecta reuso de un
     * token ya rotado, revoca toda la familia activa del usuario. La revocación
     * de seguridad debe persistir aunque el método termine en 401.
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResult refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(InvalidCredentialsException::new);
        if (stored.isRevoked()) {
            // El cliente presentó un token ya usado: posible robo. Se corta la familia.
            refreshTokenRepository.revokeAllActiveByUserId(stored.getUserId());
            throw new InvalidCredentialsException();
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException();
        }
        String newRaw = generateOpaqueToken();
        RefreshToken replacement = new RefreshToken(stored.getUserId(), sha256Hex(newRaw),
                Instant.now().plus(Duration.ofDays(refreshTtlDays)));
        stored.setRevoked(true);
        stored.setReplacedByHash(replacement.getTokenHash());
        refreshTokenRepository.save(stored);
        refreshTokenRepository.save(replacement);

        AppUser user = userRepository.findById(stored.getUserId())
                .orElseThrow(InvalidCredentialsException::new);
        String accessToken = jwtTokenProvider.generateToken(user.getEmail(), user.getId().toString());
        return new LoginResult(accessToken, newRaw);
    }

    /**
     * US28: revoca el refresh token y añade el access token actual a la
     * denylist. Idempotente: tokens desconocidos o ya revocados no fallan.
     */
    public void logout(String rawRefreshToken, String accessToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                    });
        }
        if (accessToken != null && !accessToken.isBlank() && jwtTokenProvider.validateToken(accessToken)) {
            tokenDenylist.revoke(accessToken, jwtTokenProvider.getExpirationEpochMs(accessToken));
        }
    }

    private String issueRefreshToken(Long userId) {
        String raw = generateOpaqueToken();
        refreshTokenRepository.save(new RefreshToken(userId, sha256Hex(raw),
                Instant.now().plus(Duration.ofDays(refreshTtlDays))));
        return raw;
    }

    /** Valor aleatorio opaco de 256 bits, no un JWT. Package: lo reutiliza AccountService. */
    static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public AppUser getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
    }

    /** US33: existencia de usuario para la compuerta interna (404 si no existe). */
    public boolean existsById(Long id) {
        return userRepository.existsById(id);
    }
}
