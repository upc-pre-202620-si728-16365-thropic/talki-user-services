package com.thropic.talki.identity.interfaces.rest;

import com.thropic.talki.identity.application.services.AuthService;
import com.thropic.talki.identity.application.services.ConsentCommandService;
import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.domain.model.VoiceConsent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * US33 — consentimiento de voz del usuario autenticado (JWT) y compuerta
 * interna para Sessions/LiveCoach. Tras un retiro, la compuerta responde
 * active=false y bloquea nuevo procesamiento aunque haya purgas pendientes
 * (el borrado distribuido es TS06 / G-PRIV, fuera de este sprint).
 */
@RestController
public class VoiceConsentController {

    private final ConsentCommandService consentService;
    private final AuthService authService;
    private final String internalApiKey;

    public VoiceConsentController(ConsentCommandService consentService,
                                  AuthService authService,
                                  @Value("${internal.api-key}") String internalApiKey) {
        this.consentService = consentService;
        this.authService = authService;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/v1/users/me/voice-consents")
    public ResponseEntity<Map<String, Object>> accept(Authentication authentication,
                                                      @RequestBody Map<String, String> body) {
        VoiceConsent consent = consentService.accept(me(authentication).getId(), body.get("policyVersion"));
        return ResponseEntity.status(201).body(toJson(consent));
    }

    @DeleteMapping("/v1/users/me/voice-consents/{consentId}")
    public ResponseEntity<Map<String, Object>> withdraw(Authentication authentication,
                                                        @PathVariable String consentId) {
        return ResponseEntity.ok(toJson(consentService.withdraw(me(authentication).getId(), consentId)));
    }

    /** Historial del usuario: todas las aceptaciones con su estado. */
    @GetMapping("/v1/users/me/voice-consents")
    public ResponseEntity<List<Map<String, Object>>> history(Authentication authentication) {
        List<Map<String, Object>> history = new ArrayList<>();
        for (VoiceConsent consent : consentService.status(me(authentication).getId())) {
            history.add(toJson(consent));
        }
        return ResponseEntity.ok(history);
    }

    /**
     * Compuerta entre servicios: Sessions/LiveCoach consultan antes de
     * procesar voz. Autenticación interna por X-Internal-Api-Key
     * (env INTERNAL_API_KEY, obligatoria sin fallback).
     */
    @GetMapping("/internal/v1/users/{userId}/voice-consent")
    public ResponseEntity<Map<String, Object>> internalStatus(@PathVariable Long userId,
                                                              @RequestParam String requiredVersion,
                                                              @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        if (!internalApiKey.equals(apiKey)) {
            return ResponseEntity.status(401)
                    .body(Map.of("status", 401, "error", "Unauthorized", "message", "Clave interna inválida"));
        }
        if (!authService.existsById(userId)) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", 404, "error", "Not Found", "message", "Usuario no encontrado"));
        }
        return ResponseEntity.ok(Map.of("active", consentService.isActiveFor(userId, requiredVersion)));
    }

    private AppUser me(Authentication authentication) {
        return authService.getByEmail(authentication.getName());
    }

    private Map<String, Object> toJson(VoiceConsent consent) {
        // LinkedHashMap: withdrawnAt puede ser null (Map.of no admite nulls).
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("consentId", consent.getId().toString());
        json.put("policyVersion", consent.getPolicyVersion());
        json.put("scope", consent.getScope());
        json.put("acceptedAt", consent.getAcceptedAt().toString());
        json.put("withdrawnAt", consent.getWithdrawnAt() == null ? null : consent.getWithdrawnAt().toString());
        return json;
    }
}
