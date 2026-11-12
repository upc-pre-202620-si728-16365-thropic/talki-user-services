package com.thropic.talki.identity.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
import com.thropic.talki.identity.infrastructure.persistence.JpaRefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flujo HTTP real (filtro de seguridad + ControllerAdvice) contra H2 en memoria.
 */
@SpringBootTest(properties = {
        "jwt.secret=talki-test-jwt-secret-must-be-at-least-256-bits-long-and-this-is-it-yes",
        "INTERNAL_API_KEY=test-internal-key"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String EMAIL = "manuel@upc.edu.pe";
    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JpaRefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.thropic.talki.identity.infrastructure.security.JwtTokenProvider jwtTokenProvider;

    @Autowired
    private com.thropic.talki.identity.application.services.LoginAttemptService loginAttemptService;
    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginAttemptService.reset(EMAIL); // el contador de intentos es compartido por el contexto
        userRepository.save(new AppUser(EMAIL, passwordEncoder.encode(PASSWORD), "ManuelTumi", "ciclos_6_10"));
    }

    private String loginBody(String password) {
        return "{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void login_withValidCredentials_shouldReturn200() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void login_withInvalidCredentials_shouldReturn401GenericMessage() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("WrongPass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    void login_whenAttemptsExhausted_shouldReturn429WithRetryAfter() throws Exception {
        // 5 fallos consecutivos agotan el límite.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("WrongPass1")))
                    .andExpect(status().isUnauthorized());
        }

        // El 6.º intento se bloquea aunque la contraseña fuera la correcta.
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    void register_withDuplicateEmail_shouldReturn409() throws Exception {
        String body = "{\"email\":\"" + EMAIL + "\",\"password\":\"Password123\","
                + "\"username\":\"ManuelTumi\",\"academicSegment\":\"ciclos_6_10\"}";

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El email ya está registrado"));
    }

    @Test
    void register_withWeakPassword_shouldReturn400ExplainingRule() throws Exception {
        String body = "{\"email\":\"nuevo@upc.edu.pe\",\"password\":\"corta\","
                + "\"username\":\"Nuevo\",\"academicSegment\":\"ciclos_6_10\"}";

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "La contraseña debe tener al menos 8 caracteres e incluir al menos una letra y un número"));
    }

    @Test
    void register_withUnknownAcademicSegment_shouldReturn400() throws Exception {
        String body = "{\"email\":\"nuevo@upc.edu.pe\",\"password\":\"Password123\","
                + "\"username\":\"Nuevo\",\"academicSegment\":\"posgrado\"}";

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "El segmento académico solo admite los valores ciclos_1_5 o ciclos_6_10"));
    }

    // ---------- US23: GET /v1/users/me protegido por filtro JWT ----------

    @Test
    void me_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withInvalidSignatureToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withValidToken_shouldReturn200AndUserData() throws Exception {
        String token = jwtTokenProvider.generateToken(EMAIL, "1");

        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.username").value("ManuelTumi"))
                .andExpect(jsonPath("$.academicSegment").value("ciclos_6_10"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    // ---------- US28: refresh con rotación y logout ----------

    private JsonNode login() throws Exception {
        String response = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private MvcResult postRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andReturn();
    }

    @Test
    void refresh_whenTokenIsValid_shouldRotate() throws Exception {
        JsonNode login = login();

        MvcResult result = postRefresh(login.get("refreshToken").asText());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode rotated = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(rotated.get("refreshToken").asText())
                .isNotEqualTo(login.get("refreshToken").asText());
        // El access token de la rotación es válido en /v1/users/me
        // (la rotación solo afecta refresh tokens).
        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + rotated.get("accessToken").asText()))
                .andExpect(status().isOk());
        // El token usado queda revocado: reusarlo falla con 401.
        assertThat(postRefresh(login.get("refreshToken").asText()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refresh_whenRotatedTokenIsReused_shouldReturn401AndRevokeFamily() throws Exception {
        JsonNode login = login();
        String original = login.get("refreshToken").asText();

        // Primera rotación legítima.
        MvcResult rotation = postRefresh(original);
        JsonNode rotated = objectMapper.readTree(rotation.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        String replacement = rotated.get("refreshToken").asText();

        // Reuso del token ya rotado → 401 y revocación de toda la familia.
        assertThat(postRefresh(original).getResponse().getStatus()).isEqualTo(401);
        assertThat(postRefresh(replacement).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logout_shouldDenyAccessTokenAndRevokeRefresh() throws Exception {
        JsonNode login = login();
        String accessToken = login.get("accessToken").asText();
        String refreshToken = login.get("refreshToken").asText();

        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        // El access token anterior ya no sirve en /v1/users/me.
        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // El refresh revocado ya no renueva.
        assertThat(postRefresh(refreshToken).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logout_repeated_shouldStayIdempotent() throws Exception {
        JsonNode login = login();
        String body = "{\"refreshToken\":\"" + login.get("refreshToken").asText() + "\"}";

        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        // Repetir logout con el mismo refresh no falla.
        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void refresh_whenTokenUnknown_shouldReturn401() throws Exception {
        assertThat(postRefresh("no-existe").getResponse().getStatus()).isEqualTo(401);
    }
}
