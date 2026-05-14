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
    private final CustomIntegrationRepository integrationRepository;
    private final CustomRuleRepository ruleRepository;

    @Override
    public void run(String... args) {
        if (incidentRepository.count() == 0) {
            List<User> users = userRepository.findAll();
            if (!users.isEmpty()) {
                User user = users.get(0);
                
                // ... (previous incident seeding)
                
                // Seed a sample rule for any existing custom integration
                List<CustomIntegration> integrations = integrationRepository.findAllByUser(user);
                if (!integrations.isEmpty() && ruleRepository.count() == 0) {
                    ruleRepository.save(CustomRule.builder()
                            .integration(integrations.get(0))
                            .conditionField("status")
                            .conditionValue("failed")
                            .actionType("INCIDENT")
                            .build());
                }
            }
        }
    }
}
