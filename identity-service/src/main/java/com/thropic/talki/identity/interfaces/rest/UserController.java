package com.thropic.talki.identity.interfaces.rest;

import com.thropic.talki.identity.application.services.AuthService;
import com.thropic.talki.identity.domain.model.AppUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/** US23: datos del usuario autenticado vía JWT. */
@org.springframework.web.bind.annotation.RestController
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/v1/users/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        AppUser user = authService.getByEmail(authentication.getName());
        return ResponseEntity.ok(Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "username", user.getUsername(),
                "academicSegment", user.getAcademicSegment(),
                "role", user.getRole().name()
        ));
    }
}
