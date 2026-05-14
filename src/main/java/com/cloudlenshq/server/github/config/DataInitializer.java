package com.cloudlenshq.server.github.config;

import com.cloudlenshq.server.github.entity.AuditLog;
import com.cloudlenshq.server.github.entity.Runner;
import com.cloudlenshq.server.github.repository.AuditLogRepository;
import com.cloudlenshq.server.github.repository.RunnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RunnerRepository runnerRepository;
    private final AuditLogRepository auditLogRepository;

    @Override
    public void run(String... args) {
    }
}
