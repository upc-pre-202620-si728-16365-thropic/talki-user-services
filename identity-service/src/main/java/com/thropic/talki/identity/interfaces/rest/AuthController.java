package com.thropic.talki.identity.interfaces.rest;

import com.thropic.talki.identity.application.services.AuthService;
import com.thropic.talki.identity.domain.model.AppUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        AppUser user = authService.register(
                body.get("email"),
                body.get("password"),
                body.get("username"),
                body.getOrDefault("academicSegment", "ciclos_6_10")
        );
        return ResponseEntity.status(201).body(Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "username", user.getUsername(),
                "academicSegment", user.getAcademicSegment()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        AuthService.LoginResult result = authService.login(body.get("email"), body.get("password"));
        return ResponseEntity.ok(tokens(result));
    }

    /** US28: rota el refresh token y devuelve un nuevo par de tokens. */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        AuthService.LoginResult result = authService.refresh(body.get("refreshToken"));
        return ResponseEntity.ok(tokens(result));
    }

    /** US28: revoca el refresh token y deniega el access token actual. Idempotente. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring(7);
        }
        authService.logout(body.get("refreshToken"), accessToken);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> tokens(AuthService.LoginResult result) {
        return Map.of(
                "accessToken", result.accessToken(),
                "refreshToken", result.refreshToken(),
                "tokenType", "Bearer"
        );
    }
}
