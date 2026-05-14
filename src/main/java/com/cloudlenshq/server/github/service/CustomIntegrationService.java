package com.cloudlenshq.server.github.service;

import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.github.entity.AuditLog;
import com.cloudlenshq.server.github.entity.CustomIntegration;
import com.cloudlenshq.server.github.entity.CustomRule;
import com.cloudlenshq.server.github.entity.Incident;
import com.cloudlenshq.server.github.entity.WebhookPayload;
import com.cloudlenshq.server.github.repository.AuditLogRepository;
import com.cloudlenshq.server.github.repository.CustomIntegrationRepository;
import com.cloudlenshq.server.github.repository.CustomRuleRepository;
import com.cloudlenshq.server.github.repository.WebhookPayloadRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomIntegrationService {
    private final CustomIntegrationRepository integrationRepository;
    private final WebhookPayloadRepository payloadRepository;
    private final CustomRuleRepository ruleRepository;
    private final IncidentService incidentService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<CustomIntegration> getUserIntegrations(User user) {
        return integrationRepository.findAllByUser(user);
    }

    @Transactional
    public CustomIntegration createIntegration(User user, String name, String type, String category) {
        CustomIntegration integration = CustomIntegration.builder()
                .user(user)
                .name(name)
                .type(type)
                .category(category)
                .uniqueId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .status("Active")
                .build();
        return integrationRepository.save(integration);
    }

    @Transactional
    public CustomIntegration createIntegration(CustomIntegration integration, User user) {
        integration.setUser(user);
        integration.setUniqueId(UUID.randomUUID().toString());
        integration.setCreatedAt(LocalDateTime.now());
        integration.setStatus("Active");
        return integrationRepository.save(integration);
    }

    @Transactional
    public void handleWebhook(String uniqueId, String payload, String sourceIp) {
        CustomIntegration integration = integrationRepository.findByUniqueId(uniqueId)
                .orElseThrow(() -> new RuntimeException("Integration not found"));

        WebhookPayload webhookPayload = WebhookPayload.builder()
                .integration(integration)
                .payload(payload)
                .sourceIp(sourceIp)
                .receivedAt(LocalDateTime.now())
                .build();

        payloadRepository.save(webhookPayload);
        
        integration.setLastSeen(LocalDateTime.now());
        integrationRepository.save(integration);

        // Rule Engine Execution
        processPayloadRules(integration, payload);
    }

    private void processPayloadRules(CustomIntegration integration, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            List<CustomRule> rules = ruleRepository.findAllByIntegration(integration);

            for (CustomRule rule : rules) {
                String fieldValue = getNestedFieldValue(root, rule.getConditionField());
                if (fieldValue != null && fieldValue.equalsIgnoreCase(rule.getConditionValue())) {
                    executeRuleAction(rule, integration, root);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process rules for integration {}: {}", integration.getUniqueId(), e.getMessage());
        }
    }

    private String getNestedFieldValue(JsonNode node, String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) return null;
        String[] parts = fieldPath.split("\\.");
        JsonNode current = node;
        for (String part : parts) {
            current = current.get(part);
            if (current == null) return null;
        }
        return current.asText();
    }

    private void executeRuleAction(CustomRule rule, CustomIntegration integration, JsonNode payload) {
        log.info("Executing action {} for rule {} on integration {}", rule.getActionType(), rule.getId(), integration.getUniqueId());
        
        if ("INCIDENT".equalsIgnoreCase(rule.getActionType())) {
            incidentService.createIncident(Incident.builder()
                    .title("Automated Incident: " + integration.getName())
                    .service(integration.getName())
                    .severity("critical")
                    .status("Investigating")
                    .description("Auto-generated incident from Custom Integration rule match.\nRule: " + rule.getConditionField() + "=" + rule.getConditionValue())
                    .impact("Automated Detection")
                    .owner("CloudLens Bot")
                    .user(integration.getUser())
                    .build(), integration.getUser());
        } else if ("AUDIT_LOG".equalsIgnoreCase(rule.getActionType())) {
            auditLogRepository.save(AuditLog.builder()
                    .action("RULE_MATCH")
                    .target(integration.getName())
                    .initials("AI")
                    .color("bg-blue-500")
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    public List<WebhookPayload> getRecentPayloads(Long id) {
        CustomIntegration integration = integrationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Integration not found"));
        
        return payloadRepository.findAllByIntegrationOrderByReceivedAtDesc(integration, PageRequest.of(0, 10));
    }
}
