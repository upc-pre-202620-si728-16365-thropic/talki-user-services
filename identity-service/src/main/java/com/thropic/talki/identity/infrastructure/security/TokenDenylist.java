package com.thropic.talki.identity.infrastructure.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Denylist de access tokens en memoria (logout). Cada entrada vive hasta la
 * expiración natural del token; al verificar se purgan las vencidas.
 */
// ponytail: en memoria, sobrevive solo a esta instancia; Redis si hay múltiples réplicas.
@Component
public class TokenDenylist {

    private final Map<String, Long> revokedUntil = new ConcurrentHashMap<>();

    public void revoke(String token, long expiresAtEpochMs) {
        revokedUntil.put(token, expiresAtEpochMs);
    }

    public boolean isDenylisted(String token) {
        long now = System.currentTimeMillis();
        revokedUntil.values().removeIf(expiry -> expiry <= now);
        return revokedUntil.containsKey(token);
    }
}
