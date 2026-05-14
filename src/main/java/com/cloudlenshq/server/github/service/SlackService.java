package com.cloudlenshq.server.github.service;

import com.cloudlenshq.server.github.entity.Integration;
import com.cloudlenshq.server.github.repository.IntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.Map;
import com.cloudlenshq.server.github.entity.SlackWorkspace;

@Service
@Slf4j
@RequiredArgsConstructor
public class SlackService {

    private final IntegrationRepository integrationRepository;
    private final RestTemplate restTemplate = new RestTemplate();


    public void sendFailureNotification(SlackWorkspace workspace, String channel, String pipelineName, String branch, String sha, String error, String logSnippet, String repoName, String runUrl, String committerName, String committerEmail) {
        if (workspace == null || (workspace.getBotToken() == null && workspace.getWebhookUrl() == null)) {
            log.warn("Cannot send Slack notification: No workspace/token/webhook provided. Workspace: {}", 
                workspace != null ? workspace.getTeamName() : "null");
            return;
        }
        
        log.info("Preparing Slack failure notification for repository: {} (Channel: {})", repoName, channel);

        try {
            String targetChannel = channel != null ? channel : (workspace.getDefaultChannel() != null ? workspace.getDefaultChannel() : "#general");
            
            java.util.List<Map<String, Object>> blocks = new java.util.ArrayList<>();
            
            blocks.add(Map.of(
                "type", "header",
                "text", Map.of("type", "plain_text", "text", "🚨 Pipeline Failure Detected", "emoji", true)
            ));

            String committerInfo = (committerName != null && !committerName.equals("unknown")) 
                ? String.format("\n*Committer:* %s (<mailto:%s|%s>)", committerName, committerEmail, committerEmail)
                : "";

            blocks.add(Map.of(
                "type", "section",
                "text", Map.of(
                    "type", "mrkdwn",
                    "text", String.format("*Repository:* `%s`\n*Pipeline:* `%s`\n*Branch:* `%s`\n*Commit:* `%s`%s", repoName, pipelineName, branch, sha, committerInfo)
                )
            ));

            if (logSnippet != null && !logSnippet.isBlank()) {
                blocks.add(Map.of(
                    "type", "section",
                    "text", Map.of(
                        "type", "mrkdwn",
                        "text", "*Error Logs Snippet:*\n```" + logSnippet + "```"
                    )
                ));
            } else {
                blocks.add(Map.of(
                    "type", "section",
                    "text", Map.of(
                        "type", "mrkdwn",
                        "text", String.format("❌ *Error:* %s", error)
                    )
                ));
            }

            blocks.add(Map.of(
                "type", "actions",
                "elements", java.util.List.of(
                    Map.of(
                        "type", "button",
                        "text", Map.of("type", "plain_text", "text", "View on GitHub"),
                        "url", runUrl != null ? runUrl : "https://github.com",
                        "style", "danger"
                    ),
                    Map.of(
                        "type", "button",
                        "text", Map.of("type", "plain_text", "text", "Open Dashboard"),
                        "url", "http://localhost:5173/dashboard"
                    )
                )
            ));

            blocks.add(Map.of(
                "type", "context",
                "elements", java.util.List.of(
                    Map.of(
                        "type", "mrkdwn",
                        "text", "CloudLens AI is analyzing this failure... (Coming soon)"
                    )
                )
            ));

            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("channel", targetChannel);
            payload.put("text", "🚨 Pipeline Failure: " + repoName);
            payload.put("blocks", blocks);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            String url;
            if (workspace.getWebhookUrl() != null && !workspace.getWebhookUrl().isBlank()) {
                url = workspace.getWebhookUrl();
            } else {
                url = "https://slack.com/api/chat.postMessage";
                headers.setBearerAuth(workspace.getBotToken());
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            log.debug("Sending Slack payload to {}: {}", url, payload);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            String body = response.getBody();
            
            if (response.getStatusCode().is2xxSuccessful() && body != null && (body.contains("\"ok\":true") || body.equals("ok"))) {
                log.info("SUCCESS: Slack notification delivered to channel {} for repository {}", targetChannel, repoName);
            } else {
                log.error("FAILURE: Slack API error. Status: {} - Body: {}", response.getStatusCode(), body);
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("HTTP ERROR calling Slack: {} - Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("CRITICAL ERROR sending Slack notification for {}: {}", repoName, e.getMessage(), e);
        }
    }

    public void sendWelcomeMessage(SlackWorkspace workspace) {
        try {
            Map<String, Object> payload = Map.of(
                "channel", workspace.getAuthedUserId(), // Send DM to installer
                "text", "CloudLens Integration Successful",
                "blocks", java.util.List.of(
                    Map.of(
                        "type", "section",
                        "text", Map.of(
                            "type", "mrkdwn",
                            "text", "🚀 *CloudLens is now connected!*"
                        )
                    ),
                    Map.of(
                        "type", "section",
                        "text", Map.of(
                            "type", "mrkdwn",
                            "text", String.format("On behalf of our founder, *Saugat Shahi*, and the CloudLens team, we're excited to help you monitor your delivery pipelines in the *%s* workspace.\n\nYou'll now receive real-time alerts for deployments, failures, and system health directly in your chosen channels.", workspace.getTeamName())
                        )
                    ),
                    Map.of(
                        "type", "context",
                        "elements", java.util.List.of(
                            Map.of(
                                "type", "mrkdwn",
                                "text", "🛠️ *Next Step:* Head over to your dashboard to configure alert preferences."
                            )
                        )
                    ),
                    Map.of(
                        "type", "divider"
                    ),
                    Map.of(
                        "type", "context",
                        "elements", java.util.List.of(
                            Map.of(
                                "type", "mrkdwn",
                                "text", "Sent via CloudLens Observability Platform"
                            )
                        )
                    )
                )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(workspace.getBotToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity("https://slack.com/api/chat.postMessage", entity, String.class);
        } catch (Exception e) {
            log.error("Failed to send welcome message: {}", e.getMessage());
        }
    }
}
