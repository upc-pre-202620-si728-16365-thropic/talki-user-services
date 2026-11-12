package com.thropic.talki.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TokenDenylistTest {

    private final TokenDenylist denylist = new TokenDenylist();

    @Test
    void revoke_shouldMarkTokenAsDenylisted() {
        denylist.revoke("token-abc", System.currentTimeMillis() + 60_000);

        assertThat(denylist.isDenylisted("token-abc")).isTrue();
    }

    @Test
    void unknownToken_shouldNotBeDenylisted() {
        assertThat(denylist.isDenylisted("never-revoked")).isFalse();
    }

    @Test
    void entry_expiresAtTokenExpiry_ttlHastaSuExp() throws InterruptedException {
        // Entrada vence en 50ms: simula un token cuya exp llega.
        denylist.revoke("token-ttl", System.currentTimeMillis() + 50);
        assertThat(denylist.isDenylisted("token-ttl")).isTrue();

        Thread.sleep(80);

        assertThat(denylist.isDenylisted("token-ttl")).isFalse();
    }
}
