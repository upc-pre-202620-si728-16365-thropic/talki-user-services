package com.thropic.talki.identity.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thropic.talki.identity.application.events.ConsentEventPublisher;
import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
import com.thropic.talki.identity.infrastructure.persistence.JpaVoiceConsentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * US33 — flujo HTTP real (filtro JWT + ControllerAdvice) contra H2 en memoria.
 * El publisher de eventos se simula: no hay broker en pruebas unitarias de integración.
 */
@SpringBootTest(properties = {
        "jwt.secret=talki-test-jwt-secret-must-be-at-least-256-bits-long-and-this-is-it-yes",
        "INTERNAL_API_KEY=test-internal-key"
})
@AutoConfigureMockMvc
class VoiceConsentFlowIntegrationTest {

    private static final String EMAIL = "manuel@upc.edu.pe";
    private static final String PASSWORD = "Password123";
    private static final String INTERNAL_KEY = "test-internal-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JpaVoiceConsentRepository voiceConsentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.thropic.talki.identity.infrastructure.security.JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ConsentEventPublisher consentEventPublisher;

    private String token;

    @BeforeEach
    void setUp() {
        voiceConsentRepository.deleteAll();
        userRepository.deleteAll();
        AppUser user = userRepository.save(
                new AppUser(EMAIL, passwordEncoder.encode(PASSWORD), "ManuelTumi", "ciclos_6_10"));
        token = jwtTokenProvider.generateToken(EMAIL, user.getId().toString());
    }

    private String acceptBody(String policyVersion) {
        return "{\"policyVersion\":\"" + policyVersion + "\"}";
    }

    private MvcResult accept(String policyVersion) throws Exception {
        return mockMvc.perform(post("/v1/users/me/voice-consents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody(policyVersion)))
                .andReturn();
    }

    private String acceptedConsentId(String policyVersion) throws Exception {
        MvcResult result = accept(policyVersion);
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("consentId").asText();
    }

    private MvcResult internalStatus(Long userId, String requiredVersion, String apiKey) throws Exception {
        var request = get("/internal/v1/users/" + userId + "/voice-consent")
                .param("requiredVersion", requiredVersion);
        if (apiKey != null) {
            request.header("X-Internal-Api-Key", apiKey);
        }
        return mockMvc.perform(request).andReturn();
    }

    // ---------- POST /v1/users/me/voice-consents ----------

    @Test
    void accept_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/v1/users/me/voice-consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody("v1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accept_shouldReturn201WithConsentState() throws Exception {
        mockMvc.perform(post("/v1/users/me/voice-consents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody("v1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consentId").isString())
                .andExpect(jsonPath("$.policyVersion").value("v1"))
                .andExpect(jsonPath("$.scope").value("voice_processing"))
                .andExpect(jsonPath("$.acceptedAt").isString())
                .andExpect(jsonPath("$.withdrawnAt").doesNotExist());
    }

    @Test
    void accept_withBlankPolicyVersion_shouldReturn400() throws Exception {
        mockMvc.perform(post("/v1/users/me/voice-consents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La versión de política de voz es obligatoria"));
    }

    @Test
    void accept_duplicateActiveConsent_shouldReturn409() throws Exception {
        accept("v1");

        mockMvc.perform(post("/v1/users/me/voice-consents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody("v1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Ya existe un consentimiento de voz activo para esta versión de política"));
    }

    // ---------- DELETE + GET historial ----------

    @Test
    void withdraw_thenHistoryShowsWithdrawnAtButKeepsTheRow() throws Exception {
        String consentId = acceptedConsentId("v1");

        mockMvc.perform(delete("/v1/users/me/voice-consents/" + consentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentId").value(consentId))
                .andExpect(jsonPath("$.withdrawnAt").isString());

        // Regla Gherkin: retirar NO elimina el historial — la fila sigue ahí.
        MvcResult history = mockMvc.perform(get("/v1/users/me/voice-consents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(history.getResponse().getContentAsString(StandardCharsets.UTF_8));
        org.assertj.core.api.Assertions.assertThat(rows).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(rows.get(0).get("withdrawnAt").isNull()).isFalse();
    }

    @Test
    void withdraw_withUnknownConsentId_shouldReturn404() throws Exception {
        acceptedConsentId("v1");

        mockMvc.perform(delete("/v1/users/me/voice-consents/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /internal/v1/users/{id}/voice-consent (compuerta) ----------

    @Test
    void internal_afterAccept_shouldReportActiveTrue() throws Exception {
        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        accept("v1");

        mockMvc.perform(get("/internal/v1/users/" + userId + "/voice-consent")
                        .param("requiredVersion", "v1")
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void internal_afterWithdraw_shouldReportActiveFalse_andBlockProcessing() throws Exception {
        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        String consentId = acceptedConsentId("v1");

        mockMvc.perform(delete("/v1/users/me/voice-consents/" + consentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Contrato US33 (flujo alternativo): tras el retiro la compuerta bloquea
        // aunque la purga de sesiones quede pendiente (TS06/G-PRIV).
        mockMvc.perform(get("/internal/v1/users/" + userId + "/voice-consent")
                        .param("requiredVersion", "v1")
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void internal_withoutApiKey_shouldReturn401() throws Exception {
        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        mockMvc.perform(get("/internal/v1/users/" + userId + "/voice-consent")
                        .param("requiredVersion", "v1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internal_withWrongApiKey_shouldReturn401() throws Exception {
        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        mockMvc.perform(get("/internal/v1/users/" + userId + "/voice-consent")
                        .param("requiredVersion", "v1")
                        .header("X-Internal-Api-Key", "clave-incorrecta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internal_withHigherRequiredVersion_shouldReturnFalse() throws Exception {
        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        accept("v1");

        mockMvc.perform(get("/internal/v1/users/" + userId + "/voice-consent")
                        .param("requiredVersion", "v2")
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void internal_withUnknownUser_shouldReturn404() throws Exception {
        mockMvc.perform(get("/internal/v1/users/99999/voice-consent")
                        .param("requiredVersion", "v1")
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isNotFound());
    }
}
