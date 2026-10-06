package com.bis.assistant.config;

import com.bis.assistant.model.User;
import com.bis.assistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.email:${ADMIN_EMAIL:}}")
    private String bootstrapEmail;

    @Value("${admin.bootstrap.password:${ADMIN_PASSWORD:}}")
    private String bootstrapPassword;

    @Value("${admin.bootstrap.name:${ADMIN_NAME:BIS User}}")
    private String bootstrapName;

    @Override
    public void run(ApplicationArguments args) {
        // Normalize any existing accounts to standard USER role
        userRepository.findAll().forEach(u -> {
            if (u.getRole() != User.Role.USER) {
                u.setRole(User.Role.USER);
                userRepository.save(u);
                log.info("Migrated account {} to standard USER role", u.getEmail());
            }
        });

        if (bootstrapEmail == null || bootstrapEmail.isBlank() ||
            bootstrapPassword == null || bootstrapPassword.isBlank()) {
            return;
        }

        String email = bootstrapEmail.trim().toLowerCase();
        var existingOpt = userRepository.findByEmail(email);
        if (existingOpt.isPresent()) {
            User existing = existingOpt.get();
            existing.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
            existing.setRole(User.Role.USER);
            existing.setActive(true);
            userRepository.save(existing);
            log.info("Bootstrap user account updated with configured credentials for {}", email);
            return;
        }

        User user = User.builder()
            .email(email)
            .name(bootstrapName.trim())
            .passwordHash(passwordEncoder.encode(bootstrapPassword))
            .role(User.Role.USER)
            .preferredLang("en")
            .active(true)
            .emailVerified(true)
            .build();

        userRepository.save(user);
        log.info("Secure bootstrap: created initial user account for {}", email);
    }
}
