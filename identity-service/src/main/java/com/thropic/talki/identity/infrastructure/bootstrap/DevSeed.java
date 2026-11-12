package com.thropic.talki.identity.infrastructure.bootstrap;

import com.thropic.talki.identity.domain.model.AppUser;
import com.thropic.talki.identity.infrastructure.persistence.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Siembra la cuenta de demostración del informe AV4 SOLO con el perfil dev
 * (docker-compose.dev.yml). Reemplaza al backdoor DEMO_EMAIL de register.
 */
@Configuration
@Profile("dev")
public class DevSeed {

    private static final Logger log = LoggerFactory.getLogger(DevSeed.class);

    // ponytail: credenciales demo fijas, solo existentes en perfil dev local.
    private static final String DEMO_EMAIL = "alejo.demo.av4@upc.edu.pe";
    private static final String DEMO_PASSWORD = "Demo1234!";

    @Bean
    CommandLineRunner seedDemoUser(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByEmail(DEMO_EMAIL)) {
                return; // idempotente: si ya existe, no se toca
            }
            AppUser demo = new AppUser(
                    DEMO_EMAIL,
                    passwordEncoder.encode(DEMO_PASSWORD),
                    "alejo_demo",
                    "ciclos_6_10"
            );
            // La cuenta demo del informe nace verificada (no recibe flujo de verificación).
            demo.setEmailVerified(true);
            userRepository.save(demo);
            log.info("[identity-service] Cuenta demo sembrada para perfil dev — userId={}",
                    userRepository.findByEmail(DEMO_EMAIL).map(AppUser::getId).orElse(null));
        };
    }
}
