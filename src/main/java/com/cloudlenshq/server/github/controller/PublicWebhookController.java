package com.cloudlenshq.server.github.controller;

import com.cloudlenshq.server.github.service.CustomIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hooks")
@RequiredArgsConstructor
public class PublicWebhookController {
    private final CustomIntegrationService integrationService;

    @PostMapping("/{uniqueId}")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String uniqueId,
            @RequestBody String body,
            HttpServletRequest request) {
        String sourceIp = request.getRemoteAddr();
        integrationService.handleWebhook(uniqueId, body, sourceIp);
        return ResponseEntity.ok().build();
    }
}
