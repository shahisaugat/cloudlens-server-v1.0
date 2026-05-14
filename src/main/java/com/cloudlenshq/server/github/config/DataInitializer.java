package com.cloudlenshq.server.github.config;

import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.auth.repository.UserRepository;
import com.cloudlenshq.server.github.entity.AuditLog;
import com.cloudlenshq.server.github.entity.Incident;
import com.cloudlenshq.server.github.entity.Runner;
import com.cloudlenshq.server.github.repository.AuditLogRepository;
import com.cloudlenshq.server.github.repository.IncidentRepository;
import com.cloudlenshq.server.github.repository.RunnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RunnerRepository runnerRepository;
    private final AuditLogRepository auditLogRepository;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        if (incidentRepository.count() == 0) {
            List<User> users = userRepository.findAll();
            if (!users.isEmpty()) {
                User user = users.get(0);
                
                incidentRepository.save(Incident.builder()
                        .incidentId("INC-4921")
                        .severity("critical")
                        .status("Investigating")
                        .service("auth-service")
                        .title("High Latency in JWT Validation")
                        .description("Auth service latency spiked to 4s for all /verify requests in us-east-1.")
                        .startedAt(LocalDateTime.now().minusMinutes(12))
                        .owner("Arjun M.")
                        .impact("Total login failures for 50% users")
                        .acknowledged(false)
                        .user(user)
                        .build());

                incidentRepository.save(Incident.builder()
                        .incidentId("INC-4920")
                        .severity("warning")
                        .status("Identified")
                        .service("api-gateway")
                        .title("502 Bad Gateway - Redis Timeout")
                        .description("Rate limiter instance is struggling with connection pool exhaustion.")
                        .startedAt(LocalDateTime.now().minusMinutes(45))
                        .owner("Saugat K.")
                        .impact("Degraded performance on profile routes")
                        .acknowledged(true)
                        .user(user)
                        .build());
            }
        }
    }
}
