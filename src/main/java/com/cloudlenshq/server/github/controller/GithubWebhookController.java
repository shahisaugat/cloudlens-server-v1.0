package com.cloudlenshq.server.github.controller;

import com.cloudlenshq.server.github.service.GithubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/github/webhooks")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class GithubWebhookController {

    private final GithubService githubService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload, 
                                            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType) {
        log.info("Received GitHub Webhook: {}", eventType);
        
        if ("workflow_run".equals(eventType)) {
            githubService.handleWorkflowRunEvent(payload);
        }
        
        return ResponseEntity.ok().build();
    }
}
