package com.thropic.talki.identity.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    // Secret de prueba de 64+ chars para HS256 (requisito mínimo 256 bits).
    private static final String TEST_SECRET =
            "talki-test-jwt-secret-must-be-at-least-256-bits-long-and-this-is-it-yes";
    private static final long ONE_HOUR_MINUTES = 60L;

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(TEST_SECRET, ONE_HOUR_MINUTES);
    }

    @Test
    void generateToken_shouldProduceNonBlankCompactJws() {
        String token = provider.generateToken("alejo@upc.edu.pe", "42");

        assertThat(token).isNotBlank();
        // Compact JWS = header.payload.signature
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void getEmailFromToken_shouldExtractEmailUsedAsSubject() {
        String token = provider.generateToken("manuel@upc.edu.pe", "7");

        String email = provider.getEmailFromToken(token);

        assertThat(email).isEqualTo("manuel@upc.edu.pe");
    }

    @Test
    void validateToken_whenTokenIsFreshAndSigned_shouldReturnTrue() {
        String token = provider.generateToken("gael@upc.edu.pe", "3");

        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_whenTokenIsGarbage_shouldReturnFalse() {
        assertThat(provider.validateToken("not-a-real-jwt")).isFalse();
    }

    @Test
    void validateToken_whenTokenSignedByDifferentSecret_shouldReturnFalse() {
        String otherSecret = "otro-secret-distinto-que-tambien-mide-256-bits-de-largo-suficiente";
        JwtTokenProvider attacker = new JwtTokenProvider(otherSecret, ONE_HOUR_MINUTES);
        String forged = attacker.generateToken("intruso@x.com", "999");

        assertThat(provider.validateToken(forged)).isFalse();
    }

    @Test
    void validateToken_whenTokenIsExpired_shouldReturnFalse() throws InterruptedException {
        // Expira en 0 minutos para forzar expiración inmediata.
        JwtTokenProvider shortLived = new JwtTokenProvider(TEST_SECRET, 0L);
        String token = shortLived.generateToken("alejo@upc.edu.pe", "42");
        Thread.sleep(10);

        assertThat(shortLived.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_whenTokenIsEmpty_shouldReturnFalse() {
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void generateToken_whenCalledTwiceForSameUser_shouldProduceDifferentTokens() throws InterruptedException {
        String first = provider.generateToken("alejo@upc.edu.pe", "42");
        Thread.sleep(1000);
        String second = provider.generateToken("alejo@upc.edu.pe", "42");

        // El issuedAt es distinto en segundos, así que los tokens deben diferir.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void getEmailFromToken_whenTokenIsInvalid_shouldThrow() {
        assertThatThrownBy(() -> provider.getEmailFromToken("garbage"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void getExpirationEpochMs_shouldMatchConfiguredTtl() {
        long before = System.currentTimeMillis();
        String token = provider.generateToken("alejo@upc.edu.pe", "42");

        long exp = provider.getExpirationEpochMs(token);

        assertThat(exp).isBetween(before + 60 * 60_000L - 5_000, System.currentTimeMillis() + 60 * 60_000L + 5_000);
    }

    @Test
    void defaultTtl_is15Minutes_whenConfiguredSo() {
        JwtTokenProvider shortLived = new JwtTokenProvider(TEST_SECRET, 15L);
        long before = System.currentTimeMillis();
        String token = shortLived.generateToken("a@b.com", "1");

        long exp = shortLived.getExpirationEpochMs(token);

        assertThat(exp - before).isCloseTo(15 * 60_000L, org.assertj.core.data.Offset.offset(5_000L));
    }
}
