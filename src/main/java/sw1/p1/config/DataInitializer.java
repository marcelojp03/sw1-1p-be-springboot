package sw1.p1.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sw1.p1.auth.domain.Role;
import sw1.p1.auth.domain.User;
import sw1.p1.auth.domain.UserRepository;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@example.com}")
    private String adminEmail;

    @Value("${app.admin.password:changeme}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        boolean adminExists = userRepository.findAll().stream()
                .anyMatch(u -> u.getRoles() != null && u.getRoles().contains(Role.ADMIN));

        if (adminExists) {
            log.info("Admin ya existe, omitiendo");
            return;
        }

        User admin = User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .fullName("Administrador")
                .roles(List.of(Role.ADMIN))
                .active(true)
                .createdAt(Instant.now())
                .build();

        userRepository.save(admin);
        log.info("Usuario ADMIN inicial creado: {}", adminEmail);
    }
}
