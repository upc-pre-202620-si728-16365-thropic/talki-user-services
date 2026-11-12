package com.thropic.talki.identity.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thropic.talki.identity.application.events.AccountEventPublisher;
import com.thropic.talki.identity.application.events.UserRegisteredEventPublisher;
import com.thropic.talki.identity.domain.event.UserAccountEvent;
import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
import com.thropic.talki.identity.infrastructure.persistence.JpaEmailVerificationTokenRepository;
import com.thropic.talki.identity.infrastructure.persistence.JpaPasswordResetTokenRepository;
import com.thropic.talki.identity.infrastructure.persistence.JpaRefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flujos HTTP reales (filtro JWT + ControllerAdvice) del ciclo de vida de la
 * cuenta contra H2 en memoria. Los publishers se simulan (no hay broker);
 * el token crudo se captura del evento publicado por register/forgot.
 */
@SpringBootTest(properties = {
        "jwt.secret=talki-test-jwt-secret-must-be-at-least-256-bits-long-and-this-is-it-yes",
        "INTERNAL_API_KEY=test-internal-key"
})
@AutoConfigureMockMvc
class AccountLifecycleFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JpaEmailVerificationTokenRepository emailTokenRepository;

    @Autowired
    private JpaPasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private JpaRefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AccountEventPublisher accountEventPublisher;

    @MockBean
    private UserRegisteredEventPublisher userRegisteredEventPublisher;

    private long seq;

    @BeforeEach
    void setUp() {
        emailTokenRepository.deleteAll();
        resetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        reset(accountEventPublisher);
        reset(userRegisteredEventPublisher);
        seq = System.nanoTime();
    }

    private String nextEmail() {
        return "life-" + seq++ + "@upc.edu.pe";
    }

    private JsonNode register(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\","
                                + "\"username\":\"ManuelTumi\",\"academicSegment\":\"ciclos_6_10\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Token crudo del último evento del tipo dado capturado del publisher simulado. */
    private String capturedToken(String eventType) {
        ArgumentCaptor<UserAccountEvent> captor = ArgumentCaptor.forClass(UserAccountEvent.class);
        verify(accountEventPublisher, org.mockito.Mockito.atLeastOnce()).publish(captor.capture());
        List<UserAccountEvent> matches = captor.getAllValues().stream()
                .filter(e -> eventType.equals(e.getEventType()))
                .collect(Collectors.toList());
        assertThat(matches).isNotEmpty();
        return matches.get(matches.size() - 1).getToken();
    }

    // ---------- Verificación de correo ----------

    @Test
    void register_verifyEmail_meShowsVerified_reverifyWithSameToken_fails() throws Exception {
        String email = nextEmail();
        register(email, "Password123");
        String rawToken = capturedToken(UserAccountEvent.TYPE_EMAIL_VERIFICATION_REQUESTED);
        assertThat(rawToken).isNotBlank();

        mockMvc.perform(post("/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        JsonNode tokens = login(email, "Password123");
        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        // El token ya se consumió: reutilizarlo da 400 genérico.
        mockMvc.perform(post("/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El token es inválido, expiró o ya fue utilizado"));
    }

    @Test
    void resendVerification_withoutToken_401_andFourthCallInWindow_429() throws Exception {
        String email = nextEmail();
        register(email, "Password123");
        JsonNode tokens = login(email, "Password123");
        String auth = "Bearer " + tokens.get("accessToken").asText();

        mockMvc.perform(post("/v1/users/me/resend-verification"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/users/me/resend-verification").header("Authorization", auth))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/v1/users/me/resend-verification").header("Authorization", auth))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/v1/users/me/resend-verification").header("Authorization", auth))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/v1/users/me/resend-verification").header("Authorization", auth))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    // ---------- Recuperación de contraseña ----------

    @Test
    void forgotPassword_unknownEmail_sameGenericResponseAsKnownEmail() throws Exception {
        String generic = "Si el correo está registrado, recibirás un enlace de recuperación";

        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fantasma-" + seq + "@upc.edu.pe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(generic));

        // Con email existente la respuesta es idéntica (anti-enumeración).
        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + nextEmail() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_resetPassword_loginWithNewPassword_oldRefreshIsRevoked() throws Exception {
        String email = nextEmail();
        register(email, "Password123");
        JsonNode oldTokens = login(email, "Password123");

        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        String resetToken = capturedToken(UserAccountEvent.TYPE_PASSWORD_RESET_REQUESTED);

        // Contraseña débil → 400 con la regla (el token no se consume).
        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "La contraseña debe tener al menos 8 caracteres e incluir al menos una letra y un número"));

        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"NewPassword456\"}"))
                .andExpect(status().isOk());

        // La nueva contraseña entra; el refresh anterior murió con el reset.
        assertThat(login(email, "NewPassword456").get("accessToken").asText()).isNotBlank();

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldTokens.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized());

        // El token de reset ya se consumió: reusarlo da 400 genérico.
        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"OtherPass789\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El token es inválido, expiró o ya fue utilizado"));
    }

    // ---------- PATCH /v1/users/me ----------

    @Test
    void patchMe_withoutToken_401() throws Exception {
        mockMvc.perform(patch("/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchMe_withUnknownSegment_400() throws Exception {
        String email = nextEmail();
        register(email, "Password123");
        JsonNode tokens = login(email, "Password123");

        mockMvc.perform(patch("/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicSegment\":\"posgrado\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "El segmento académico solo admite los valores ciclos_1_5 o ciclos_6_10"));
    }

    @Test
    void patchMe_happyPath_persistsChanges_andEmailIsIgnoredFromBody() throws Exception {
        String email = nextEmail();
        register(email, "Password123");
        JsonNode tokens = login(email, "Password123");
        String auth = "Bearer " + tokens.get("accessToken").asText();

        // El email llega en el body pero NO es editable en este slice: se ignora.
        mockMvc.perform(patch("/v1/users/me")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"NuevoNombre\",\"academicSegment\":\"ciclos_1_5\","
                                + "\"email\":\"hack@evil.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("NuevoNombre"))
                .andExpect(jsonPath("$.academicSegment").value("ciclos_1_5"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerified").value(false));

        // Persistido: el GET refleja los cambios.
        mockMvc.perform(get("/v1/users/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("NuevoNombre"))
                .andExpect(jsonPath("$.academicSegment").value("ciclos_1_5"));
    }
}
