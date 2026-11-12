package com.thropic.talki.identity.interfaces.rest;

import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flujo HTTP real (filtro de seguridad + ControllerAdvice) contra H2 en memoria.
 */
@SpringBootTest(properties = {
        "jwt.secret=talki-test-jwt-secret-must-be-at-least-256-bits-long-and-this-is-it-yes"
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.thropic.talki.identity.application.services.LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
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
}
