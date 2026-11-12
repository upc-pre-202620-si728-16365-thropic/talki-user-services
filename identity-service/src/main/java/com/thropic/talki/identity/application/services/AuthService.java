package com.thropic.talki.identity.application.services;

import com.thropic.talki.identity.application.events.UserRegisteredEventPublisher;
import com.thropic.talki.identity.domain.event.UserRegisteredEvent;
import com.thropic.talki.identity.domain.exception.DuplicateEmailException;
import com.thropic.talki.identity.domain.exception.InvalidCredentialsException;
import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
import com.thropic.talki.identity.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /** Cuenta fija de demostración (informe AV4 / Sprint 3). Se elimina en la tarea de privacidad. */
    private static final String DEMO_EMAIL = "alejo.demo.av4@upc.edu.pe";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRegisteredEventPublisher userRegisteredEventPublisher;

    public AuthService(AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       UserRegisteredEventPublisher userRegisteredEventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRegisteredEventPublisher = userRegisteredEventPublisher;
    }

    public AppUser register(String email, String rawPassword, String username, String academicSegment) {
        if (userRepository.existsByEmail(email)) {
            // ponytail: backdoor demo temporal (informe AV4); se elimina con la siembra por perfil dev.
            if (DEMO_EMAIL.equalsIgnoreCase(email)) {
                AppUser demo = userRepository.findByEmail(email)
                        .orElseThrow(InvalidCredentialsException::new);
                demo.setPasswordHash(passwordEncoder.encode(rawPassword));
                if (username != null && !username.isBlank()) {
                    demo.setUsername(username);
                }
                if (academicSegment != null && !academicSegment.isBlank()) {
                    demo.setAcademicSegment(academicSegment);
                }
                return userRepository.save(demo);
            }
            throw new DuplicateEmailException();
        }
        String hash = passwordEncoder.encode(rawPassword);
        AppUser user = new AppUser(email, hash, username, academicSegment);
        AppUser saved = userRepository.save(user);

        // Saga por coreografía: notifica a los bounded contexts downstream
        // (session-service, etc.) para que proyecten localmente al usuario.
        userRegisteredEventPublisher.publish(new UserRegisteredEvent(
                saved.getId(),
                saved.getEmail(),
                saved.getUsername(),
                saved.getAcademicSegment()
        ));
        return saved;
    }

    public String login(String email, String rawPassword) {
        // Mensaje idéntico en ambas ramas: no se revela si falló el email o la contraseña.
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return jwtTokenProvider.generateToken(email, user.getId().toString());
    }
}
