package com.thropic.talki.identity.application.services;

import com.thropic.talki.identity.domain.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class LoginAttemptServiceTest {

    private final LoginAttemptService service = new LoginAttemptService(Duration.ofMillis(80));

    @Test
    void assertAllowed_whenUnderLimit_shouldNotThrow() {
        service.recordFailure("manuel@upc.edu.pe");
        service.recordFailure("manuel@upc.edu.pe");

        assertThatCode(() -> service.assertAllowed("manuel@upc.edu.pe"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertAllowed_whenMaxAttemptsReached_shouldThrowWithRetryAfter() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("manuel@upc.edu.pe");
        }

        assertThatThrownBy(() -> service.assertAllowed("manuel@upc.edu.pe"))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessageContaining("Reintenta")
                .satisfies(e -> assertThat(((TooManyLoginAttemptsException) e).getRetryAfterSeconds())
                        .isGreaterThan(0));
    }

    @Test
    void attempts_areIsolatedByEmail() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("bloqueado@upc.edu.pe");
        }

        assertThatCode(() -> service.assertAllowed("otro@upc.edu.pe"))
                .doesNotThrowAnyException();
    }

    @Test
    void attemptsExpire_whenWindowPasses() throws InterruptedException {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("manuel@upc.edu.pe");
        }
        assertThatThrownBy(() -> service.assertAllowed("manuel@upc.edu.pe"))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        Thread.sleep(120); // TTL (ventana de 80ms) vence

        assertThatCode(() -> service.assertAllowed("manuel@upc.edu.pe"))
                .doesNotThrowAnyException();
    }

    @Test
    void reset_shouldClearAttempts() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("manuel@upc.edu.pe");
        }

        service.reset("manuel@upc.edu.pe");

        assertThatCode(() -> service.assertAllowed("manuel@upc.edu.pe"))
                .doesNotThrowAnyException();
    }
}
