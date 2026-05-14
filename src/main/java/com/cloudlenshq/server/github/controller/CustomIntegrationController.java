package com.cloudlenshq.server.github.controller;

import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.github.entity.CustomIntegration;
import com.cloudlenshq.server.github.entity.WebhookPayload;
import com.cloudlenshq.server.github.service.CustomIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/github/integrations/custom")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class CustomIntegrationController {
    private final CustomIntegrationService integrationService;

    @PostMapping
    public ResponseEntity<?> create(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> body) {
        log.info("Creating custom integration for user: {}. Body: {}", user != null ? user.getEmail() : "NULL", body);
        
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "User not authenticated"));
        }

        try {
            CustomIntegration created = integrationService.createIntegration(
                user, 
                body.get("name"), 
                body.get("type"), 
                body.get("category")
            );
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            log.error("Error creating custom integration: ", e);
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<CustomIntegration>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(integrationService.getUserIntegrations(user));
    }

    @GetMapping("/{id}/payloads")
    public ResponseEntity<List<WebhookPayload>> getPayloads(@PathVariable Long id) {
        return ResponseEntity.ok(integrationService.getRecentPayloads(id));
    }
}
