package com.cloudlenshq.server.github.controller;

import com.cloudlenshq.server.github.entity.SlackWorkspace;
import com.cloudlenshq.server.github.repository.SlackWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/slack")
@RequiredArgsConstructor
@Slf4j
public class SlackController {

    private final SlackWorkspaceRepository slackWorkspaceRepository;
    private final com.cloudlenshq.server.github.repository.IntegrationRepository integrationRepository;
    private final com.cloudlenshq.server.auth.repository.UserRepository userRepository;
    private final com.cloudlenshq.server.github.service.SlackService slackService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${slack.client-id}")
    private String clientId;

    @Value("${slack.client-secret}")
    private String clientSecret;

    @Value("${slack.redirect-uri}")
    private String redirectUri;

    @GetMapping("/install")
    public RedirectView install(@RequestParam(required = false) Long userId) {
        String state = userId != null ? userId.toString() : "anon";
        String url = String.format(
            "https://slack.com/oauth/v2/authorize?client_id=%s&scope=chat:write,commands,incoming-webhook&redirect_uri=%s&state=%s",
            clientId, redirectUri, state
        );
        return new RedirectView(url);
    }

    @GetMapping("/callback")
    public RedirectView callback(@RequestParam String code, @RequestParam(required = false) String state) {
        log.info("Received Slack OAuth callback with code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("code", code);
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "https://slack.com/api/oauth.v2.access",
            request,
            Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body != null && Boolean.TRUE.equals(body.get("ok"))) {
            String teamId = (String) ((Map) body.get("team")).get("id");
            String teamName = (String) ((Map) body.get("team")).get("name");
            String botToken = (String) body.get("access_token");
            String botUserId = (String) body.get("bot_user_id");
            String authedUserId = (String) ((Map) body.get("authed_user")).get("id");

            SlackWorkspace workspace = slackWorkspaceRepository.findByTeamId(teamId)
                .orElse(new SlackWorkspace());

            workspace.setTeamId(teamId);
            workspace.setTeamName(teamName);
            workspace.setBotToken(botToken); // TODO: Encrypt this
            workspace.setBotUserId(botUserId);
            workspace.setAuthedUserId(authedUserId);
            
            // Extract default channel from incoming_webhook if present
            if (body.get("incoming_webhook") != null) {
                Map<String, Object> webhook = (Map<String, Object>) body.get("incoming_webhook");
                workspace.setDefaultChannel((String) webhook.get("channel"));
                workspace.setWebhookUrl((String) webhook.get("url"));
            }
            
            workspace.setInstalledAt(LocalDateTime.now());

            slackWorkspaceRepository.save(workspace);
            log.info("Successfully connected Slack workspace: {}", teamName);

            // Create or update Integration record
            Long userId = null;
            try {
                userId = Long.parseLong(state);
            } catch (Exception e) {
                log.warn("Could not parse userId from state: {}", state);
            }

            if (userId != null) {
                final Long finalUserId = userId;
                userRepository.findById(userId).ifPresent(user -> {
                    workspace.setUser(user);
                    slackWorkspaceRepository.save(workspace);

                    com.cloudlenshq.server.github.entity.Integration integration = integrationRepository.findByUserAndType(user, "SLACK")
                        .orElse(com.cloudlenshq.server.github.entity.Integration.builder()
                            .user(user)
                            .type("SLACK")
                            .name("Slack #" + teamName)
                            .successCount(0)
                            .build());
                    
                    integration.setSlackWorkspace(workspace);
                    integration.setStatus("connected");
                    integrationRepository.save(integration);
                });
            } else {
                // Fallback for demo if no userId provided
                userRepository.findAll().stream().findFirst().ifPresent(user -> {
                    workspace.setUser(user);
                    slackWorkspaceRepository.save(workspace);

                    com.cloudlenshq.server.github.entity.Integration integration = integrationRepository.findByUserAndType(user, "SLACK")
                        .orElse(com.cloudlenshq.server.github.entity.Integration.builder()
                            .user(user)
                            .type("SLACK")
                            .name("Slack #" + teamName)
                            .successCount(0)
                            .build());
                    
                    integration.setSlackWorkspace(workspace);
                    integration.setStatus("connected");
                    integrationRepository.save(integration);
                });
            }

            // Send personalized welcome message from the founder
            slackService.sendWelcomeMessage(workspace);

            // Redirect back to frontend
            return new RedirectView("http://localhost:5173/dashboard?slack=success");
        } else {
            log.error("Slack OAuth failed: {}", body != null ? body.get("error") : "Unknown error");
            return new RedirectView("http://localhost:5173/dashboard?slack=error");
        }
    }


    @DeleteMapping("/disconnect")
    @Transactional
    public ResponseEntity<Void> disconnect() {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        userRepository.findByEmail(email).ifPresent(user -> {
            // Find and delete integration
            integrationRepository.findByUserAndType(user, "SLACK").ifPresent(integration -> {
                integrationRepository.delete(integration);
            });

            // Find and delete workspace
            slackWorkspaceRepository.findByUser(user).ifPresent(workspace -> {
                slackWorkspaceRepository.delete(workspace);
            });

            log.info("Disconnected Slack for user: {}", email);
        });
        
        return ResponseEntity.ok().build();
    }
}
