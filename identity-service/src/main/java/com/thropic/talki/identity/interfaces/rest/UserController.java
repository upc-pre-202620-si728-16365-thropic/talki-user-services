package com.thropic.talki.identity.interfaces.rest;

import com.thropic.talki.identity.application.services.AccountService;
import com.thropic.talki.identity.application.services.AuthService;
import com.thropic.talki.identity.domain.model.AppUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.LinkedHashMap;
import java.util.Map;

/** US23: datos del usuario autenticado vía JWT + flujos del lifecycle. */
@org.springframework.web.bind.annotation.RestController
public class UserController {

    private final AuthService authService;
    private final AccountService accountService;

    public UserController(AuthService authService, AccountService accountService) {
        this.authService = authService;
        this.accountService = accountService;
    }

    @GetMapping("/v1/users/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        return ResponseEntity.ok(toJson(authService.getByEmail(authentication.getName())));
    }

    /** Reenvía el token de verificación del usuario autenticado (3 por email / 15 min). */
    @PostMapping("/v1/users/me/resend-verification")
    public ResponseEntity<Void> resendVerification(Authentication authentication) {
        accountService.resendVerification(authentication.getName());
        return ResponseEntity.accepted().build();
    }

    /**
     * Edita perfil: {username, academicSegment} opcionales. El email no es
     * editable en este slice: si llega en el body se ignora. Mismo shape que GET.
     */
    @PatchMapping("/v1/users/me")
    public ResponseEntity<Map<String, Object>> patchMe(Authentication authentication,
                                                       @RequestBody Map<String, String> body) {
        AppUser user = authService.getByEmail(authentication.getName());
        AppUser updated = accountService.updateProfile(
                user.getId(), body.get("username"), body.get("academicSegment"));
        return ResponseEntity.ok(toJson(updated));
    }

    /** El GET y el PATCH de perfil devuelven el mismo shape (incluye emailVerified). */
    static Map<String, Object> toJson(AppUser user) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("userId", user.getId());
        json.put("email", user.getEmail());
        json.put("username", user.getUsername());
        json.put("academicSegment", user.getAcademicSegment());
        json.put("role", user.getRole().name());
        json.put("emailVerified", user.isEmailVerified());
        return json;
    }
}
