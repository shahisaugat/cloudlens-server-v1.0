package com.cloudlenshq.server.github.service;

import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.github.entity.CustomIntegration;
import com.cloudlenshq.server.github.entity.WebhookPayload;
import com.cloudlenshq.server.github.repository.CustomIntegrationRepository;
import com.cloudlenshq.server.github.repository.WebhookPayloadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomIntegrationService {
    private final CustomIntegrationRepository integrationRepository;
    private final WebhookPayloadRepository payloadRepository;

    @Transactional
    public CustomIntegration createIntegration(User user, String name, String type, String category) {
        CustomIntegration integration = CustomIntegration.builder()
            .user(user)
            .name(name)
            .type(type)
            .category(category)
            .uniqueId(UUID.randomUUID().toString().replace("-", "").substring(0, 16))
            .status("active")
            .build();
        return integrationRepository.save(integration);
    }

    public List<CustomIntegration> getUserIntegrations(User user) {
        return integrationRepository.findAllByUser(user);
    }

    @Transactional
    public void handleWebhook(String uniqueId, String rawPayload, String sourceIp) {
        CustomIntegration integration = integrationRepository.findByUniqueId(uniqueId)
            .orElseThrow(() -> new RuntimeException("Integration not found"));
        
        WebhookPayload payload = WebhookPayload.builder()
            .integration(integration)
            .payload(rawPayload)
            .sourceIp(sourceIp)
            .statusCode(200)
            .build();
        
        payloadRepository.save(payload);
        
        integration.setLastSeen(LocalDateTime.now());
        integrationRepository.save(integration);
        
        log.info("Received webhook for custom integration: {}", integration.getName());
        // TODO: In Phase 3, trigger transformation and mapping logic here
    }

    public List<WebhookPayload> getRecentPayloads(Long integrationId) {
        CustomIntegration integration = integrationRepository.findById(integrationId)
            .orElseThrow(() -> new RuntimeException("Integration not found"));
        return payloadRepository.findAllByIntegrationOrderByReceivedAtDesc(integration, PageRequest.of(0, 20));
    }
}
