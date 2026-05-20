package com.cloudlenshq.server.github.service;

import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.auth.entity.Team;
import com.cloudlenshq.server.auth.repository.UserRepository;
import com.cloudlenshq.server.github.entity.AuditLog;
import com.cloudlenshq.server.github.entity.Deployment;
import com.cloudlenshq.server.github.entity.Integration;
import com.cloudlenshq.server.github.entity.Runner;
import com.cloudlenshq.server.github.repository.AuditLogRepository;
import com.cloudlenshq.server.github.repository.DeploymentRepository;
import com.cloudlenshq.server.github.repository.IntegrationRepository;
import com.cloudlenshq.server.github.repository.RunnerRepository;
import com.cloudlenshq.server.github.repository.SlackWorkspaceRepository;
import com.cloudlenshq.server.auth.repository.TeamRepository;
import com.cloudlenshq.server.github.entity.SlackWorkspace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GithubService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final SlackService slackService;
    private final SlackWorkspaceRepository slackWorkspaceRepository;
    private final AuditLogRepository auditLogRepository;
    private final IntegrationRepository integrationRepository;
    private final RunnerRepository runnerRepository;
    private final DeploymentRepository deploymentRepository;
    private final TeamRepository teamRepository;
    private static final String GITHUB_API_URL = "https://api.github.com";

    // Seeding logic removed to ensure clean start

    public List<Map<String, Object>> getUserRepositories() {
        String token = getGithubTokenForCurrentUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<List> response = restTemplate.exchange(
                GITHUB_API_URL + "/user/repos?sort=updated&per_page=100",
                HttpMethod.GET,
                entity,
                List.class
        );

        return response.getBody();
    }

    public List<Map<String, Object>> getWorkflowRuns(String owner, String repo) {
        String token = getGithubTokenForCurrentUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/runs?per_page=100",
                HttpMethod.GET,
                entity,
                Map.class
        );

        List<Map<String, Object>> runs = (List<Map<String, Object>>) response.getBody().get("workflow_runs");
        List<Map<String, Object>> pipelines = runs.stream().map(this::mapWorkflowRunToPipeline).toList();

        // If the latest pipeline failed, send a Slack notification
        if (!pipelines.isEmpty()) {
            Map<String, Object> latest = pipelines.get(0);
            String status = (String) latest.get("status");
            String sha = (String) latest.get("sha");
            
            if ("failed".equals(status)) {
                User user = getCurrentUser();
                
                // Synchronize on the user ID to prevent race conditions during notification firing
                synchronized (user.getId().toString().intern()) {
                    log.info("Checking Slack alert for {}/{} (SHA: {})", owner, repo, sha);
                    
                    integrationRepository.findByUserAndType(user, "SLACK")
                        .ifPresent(integration -> {
                            // Deduplication: Only send if this failure hasn't been notified yet
                            if (integration.getLastFired() == null || !sha.equals(integration.getWebhookUrl())) {
                                try {
                                    Object rawId = latest.get("id");
                                    Long runId = (rawId instanceof Number) ? ((Number) rawId).longValue() : Long.parseLong(rawId.toString());
                                    
                                    String logSnippet = getFailedJobLogs(owner, repo, runId);
                                    SlackWorkspace workspace = integration.getSlackWorkspace();
                                    
                                    if (workspace != null) {
                                        log.info("Triggering Slack alert for {}/{} (SHA: {})", owner, repo, sha);
                                        slackService.sendFailureNotification(
                                            workspace,
                                            workspace.getDefaultChannel(),
                                            (String) latest.get("name"),
                                            (String) latest.get("branch"),
                                            sha,
                                            (String) latest.get("commitMsg"),
                                            logSnippet,
                                            repo,
                                            "https://github.com/" + owner + "/" + repo + "/actions/runs/" + runId,
                                            (String) latest.get("committerName"),
                                            (String) latest.get("committerEmail")
                                        );
                                        
                                        // Update integration state to prevent double-firing
                                        integration.setLastFired(java.time.LocalDateTime.now());
                                        integration.setWebhookUrl(sha); // Store SHA to detect new failures
                                        integrationRepository.save(integration);
                                    } else {
                                        log.warn("Slack integration found but no workspace linked for user: {}", user.getEmail());
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to process Slack notification: {}", e.getMessage(), e);
                                }
                            } else {
                                log.debug("Skipping Slack notification: SHA {} already notified at {}", sha, integration.getLastFired());
                            }
                        });
                }
            }
        }

        return pipelines;
    }

    private Map<String, Object> mapWorkflowRunToPipeline(Map<String, Object> run) {
        String status = (String) run.get("status");
        String conclusion = (String) run.get("conclusion");
        
        // Map GitHub status to frontend status
        String mappedStatus = "queued";
        if ("completed".equals(status)) {
            mappedStatus = "success".equals(conclusion) ? "success" : "failed";
        } else if ("in_progress".equals(status)) {
            mappedStatus = "running";
        }

        Map<String, Object> headCommit = (Map<String, Object>) run.get("head_commit");
        String sha = (String) run.get("head_sha");
        String shortSha = sha != null ? sha.substring(0, 7) : "unknown";
        
        Map<String, Object> actor = (Map<String, Object>) run.get("actor");
        Map<String, Object> repository = (Map<String, Object>) run.get("repository");

        // Mock spark data for now
        List<Integer> spark = List.of(1, 1, 1, 0, 1, 1, 1, 1, 0, 1);

        List<Map<String, Object>> pullRequests = (List<Map<String, Object>>) run.get("pull_requests");
        String pr = (pullRequests != null && !pullRequests.isEmpty()) 
            ? "#" + pullRequests.get(0).get("number") 
            : "#" + run.get("run_number");

        Map<String, Object> pipeline = new java.util.HashMap<>();
        pipeline.put("id", run.get("id"));
        pipeline.put("name", run.get("name"));
        pipeline.put("branch", run.get("head_branch"));
        pipeline.put("sha", shortSha);
        pipeline.put("commitMsg", headCommit != null ? headCommit.get("message") : "No commit message");
        pipeline.put("pr", pr);
        pipeline.put("status", mappedStatus);
        pipeline.put("time", run.get("created_at"));
        pipeline.put("duration", calculateDuration(run));
        pipeline.put("owner", actor != null ? actor.get("login") : "unknown");
        pipeline.put("actorAvatar", actor != null ? actor.get("avatar_url") : "");
        pipeline.put("repoName", repository != null ? repository.get("name") : "unknown");
        pipeline.put("spark", spark);
        pipeline.put("triggeredBy", run.get("event"));
        
        if (headCommit != null) {
            Map<String, Object> author = (Map<String, Object>) headCommit.get("author");
            if (author != null) {
                pipeline.put("committerName", author.get("name"));
                pipeline.put("committerEmail", author.get("email"));
            } else {
                pipeline.put("committerName", "unknown");
                pipeline.put("committerEmail", "unknown");
            }
        } else {
            pipeline.put("committerName", "unknown");
            pipeline.put("committerEmail", "unknown");
        }

        pipeline.put("stages", List.of());
        
        return pipeline;
    }

    private String calculateDuration(Map<String, Object> run) {
        String createdAtStr = (String) run.get("created_at");
        String updatedAtStr = (String) run.get("updated_at");
        
        if (createdAtStr == null || updatedAtStr == null) return "—";
        
        try {
            java.time.Instant start = java.time.Instant.parse(createdAtStr);
            java.time.Instant end = java.time.Instant.parse(updatedAtStr);
            long seconds = java.time.Duration.between(start, end).getSeconds();
            if (seconds <= 0) return "—";
            if (seconds < 60) return seconds + "s";
            if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        } catch (Exception e) {
            return "—";
        }
    }

    public String getJobLogs(String owner, String repo, Long jobId) {
        try {
            String token = getGithubTokenForCurrentUser();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.github.v3+json");
            headers.set("X-GitHub-Api-Version", "2022-11-28");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // GitHub logs API returns a 302 redirect. 
            // We need to fetch the redirect URL and then get the logs from there.
            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/jobs/" + jobId + "/logs",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return response.getBody();
        } catch (Exception e) {
            System.err.println("Error fetching logs for job " + jobId + ": " + e.getMessage());
            return "Error: Could not retrieve logs from GitHub. They might have expired or the run is too old.\nDetails: " + e.getMessage();
        }
    }

    public void rerunWorkflow(String owner, String repo, Long runId) {
        String token = getGithubTokenForCurrentUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        restTemplate.exchange(
                GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/rerun",
                HttpMethod.POST,
                entity,
                Void.class
        );
    }

    public List<Map<String, Object>> getWorkflows(String owner, String repo) {
        String token = getGithubTokenForCurrentUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        
        ResponseEntity<Map> response = restTemplate.exchange(
                GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/workflows",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        
        return (List<Map<String, Object>>) response.getBody().get("workflows");
    }

    public List<Map<String, Object>> getBranches(String owner, String repo) {
        String token = getGithubTokenForCurrentUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        
        ResponseEntity<List> response = restTemplate.exchange(
                GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/branches",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class
        );
        
        return (List<Map<String, Object>>) response.getBody();
    }

    public void triggerWorkflow(String owner, String repo, String workflowId, String ref) {
        String token = getGithubTokenForCurrentUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        
        Map<String, Object> body = new HashMap<>();
        body.put("ref", ref);
        
        restTemplate.exchange(
                GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowId + "/dispatches",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Void.class
        );
        
        logAction("Triggered", "Deployment for " + repo, getCurrentUser());
    }

    public void handleWorkflowRunEvent(Map<String, Object> payload) {
        String action = (String) payload.get("action");
        Map<String, Object> run = (Map<String, Object>) payload.get("workflow_run");
        
        if (!"completed".equals(action)) {
            return;
        }
        
        String conclusion = (String) run.get("conclusion");
        if (!"failure".equals(conclusion)) {
            return;
        }
        
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        String owner = (String) ((Map<String, Object>)repository.get("owner")).get("login");
        String repoName = (String) repository.get("name");
        Long runId = ((Number) run.get("id")).longValue();
        
        log.info("Received pipeline failure webhook for {}/{}", owner, repoName);
        
        // Find all Slack integrations
        List<Integration> slackIntegrations = integrationRepository.findAll().stream()
            .filter(i -> "SLACK".equals(i.getType()) && "connected".equals(i.getStatus()))
            .toList();
            
        for (Integration integration : slackIntegrations) {
            User user = integration.getUser();
            log.info("Processing Slack integration for user: {}", user.getEmail());
            
            try {
                String logSnippet = getFailedJobLogsForUser(owner, repoName, runId, user);
                SlackWorkspace workspace = integration.getSlackWorkspace();
                
                if (workspace != null) {
                    log.info("Sending webhook notification to Slack workspace: {}", workspace.getTeamName());
                    Map<String, Object> headCommit = (Map<String, Object>) run.get("head_commit");
                    String committerName = "unknown";
                    String committerEmail = "unknown";
                    if (headCommit != null) {
                        Map<String, Object> author = (Map<String, Object>) headCommit.get("author");
                        if (author != null) {
                            committerName = (String) author.get("name");
                            committerEmail = (String) author.get("email");
                        }
                    }

                    slackService.sendFailureNotification(
                        workspace,
                        workspace.getDefaultChannel(),
                        (String)run.get("name"),
                        (String)run.get("head_branch"),
                        ((String)run.get("head_sha")).substring(0, 7),
                        "GitHub Action run failed",
                        logSnippet,
                        repoName,
                        (String)run.get("html_url"),
                        committerName,
                        committerEmail
                    );
                } else {
                    log.warn("Integration found but no workspace linked for user: {}", user.getEmail());
                }
            } catch (Exception e) {
                log.error("Error sending Slack notification from webhook: {}", e.getMessage());
            }
        }
    }

    private String getFailedJobLogsForUser(String owner, String repo, Long runId, User user) {
        try {
            String token = user.getGithubAccessToken();
            if (token == null) return null;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.github.v3+json");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/jobs",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            List<Map<String, Object>> jobs = (List<Map<String, Object>>) response.getBody().get("jobs");
            Optional<Map<String, Object>> failedJob = jobs.stream()
                .filter(job -> "failure".equals(job.get("conclusion")))
                .findFirst();
            
            if (failedJob.isPresent()) {
                Long jobId = ((Number) failedJob.get().get("id")).longValue();
                
                ResponseEntity<String> logResponse = restTemplate.exchange(
                        GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/jobs/" + jobId + "/logs",
                        HttpMethod.GET,
                        entity,
                        String.class
                );
                
                return extractLogSnippet(logResponse.getBody());
            }
        } catch (Exception e) {
            log.warn("Could not fetch logs for user {}: {}", user.getEmail(), e.getMessage());
        }
        return null;
    }

    public void updateIntegrationWebhook(User user, String type, String url) {
        User currentUser = userRepository.findById(user.getId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        String upperType = type.toUpperCase();
        Integration integration = integrationRepository.findByUserAndType(currentUser, upperType)
            .orElse(Integration.builder()
                .user(currentUser)
                .type(upperType)
                .name(type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase() + " Webhook")
                .successCount(0)
                .build());
        
        integration.setWebhookUrl(url);
        integration.setStatus("connected");
        integrationRepository.save(integration);
        
        logAction("Configured", upperType + " Integration", currentUser);
    }

    public List<Map<String, Object>> getDeployments() {
        return deploymentRepository.findAllByOrderByDeployedAtDesc().stream()
            .map(d -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", "DEP-" + d.getId());
                map.put("version", "v1.0." + d.getId()); // Simulated version
                map.put("env", d.getEnvironment());
                map.put("service", d.getRepo());
                map.put("status", d.getStatus().substring(0, 1).toUpperCase() + d.getStatus().substring(1).toLowerCase());
                map.put("startedAt", formatAgo(d.getDeployedAt()));
                map.put("duration", "4m 12s"); // Simulated duration
                map.put("author", d.getAuthor());
                map.put("commit", d.getSha().length() > 7 ? d.getSha().substring(0, 7) : d.getSha());
                map.put("strategy", "Rolling Update");
                map.put("health", "success".equals(d.getStatus().toLowerCase()) ? 100 : 0);
                return map;
            }).toList();
    }

    private String formatAgo(java.time.LocalDateTime dt) {
        if (dt == null) return "—";
        java.time.Duration duration = java.time.Duration.between(dt, java.time.LocalDateTime.now());
        long mins = duration.toMinutes();
        if (mins < 1) return "Just now";
        if (mins < 60) return mins + "m ago";
        if (mins < 1440) return (mins / 60) + "h ago";
        return (mins / 1440) + "d ago";
    }

    public Map<String, Object> getEnvironmentStatus(String owner, String repo, String environment) {
        String token = getGithubTokenForCurrentUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        
        try {
            // 1. Fetch latest deployment from GitHub
            String deploymentsUrl = GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/deployments?environment=" + environment + "&per_page=1";
            ResponseEntity<List> depResponse = restTemplate.exchange(deploymentsUrl, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            List<Map<String, Object>> deployments = depResponse.getBody();
            
            if (deployments == null || deployments.isEmpty()) {
                // Fallback to latest successful workflow run if no deployments found
                return getFallbackStatus(owner, repo);
            }
            
            Map<String, Object> deployment = deployments.get(0);
            String sha = (String) deployment.get("sha");
            
            // 2. Fetch commit details for diffFiles
            String commitUrl = GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/commits/" + sha;
            Map<String, Object> commit = restTemplate.exchange(commitUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
            
            // 3. Map to our structure
            Map<String, Object> commitInfo = (Map<String, Object>) commit.get("commit");
            Map<String, Object> authorInfo = (Map<String, Object>) commitInfo.get("author");
            
            Map<String, Object> result = new HashMap<>();
            result.put("sha", sha.substring(0, 7));
            result.put("msg", commitInfo.get("message"));
            result.put("author", authorInfo.get("name"));
            result.put("deployedAt", deployment.get("created_at"));
            
            List<Map<String, Object>> files = (List<Map<String, Object>>) commit.get("files");
            result.put("diffFiles", files != null ? files.size() : 0);
            
            // 4. Calculate queue depth (waiting runs)
            String runsUrl = GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/runs?status=queued&per_page=1";
            Map<String, Object> runsResponse = restTemplate.exchange(runsUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
            result.put("queueDepth", runsResponse.get("total_count"));
            
            // 5. Success rate and build times (mocked or derived from last 10 runs)
            result.put("successRate", "98%");
            result.put("avgBuild", "4m 12s");
            result.put("mttr", "12m");
            
            // Save to DB for history
            saveDeployment(owner, repo, environment, sha, (String)commitInfo.get("message"), (String)authorInfo.get("name"), (Integer)result.get("diffFiles"), (String)deployment.get("created_at"));
            
            return result;
        } catch (Exception e) {
            log.error("Error fetching environment status: {}", e.getMessage());
            return getFallbackStatus(owner, repo);
        }
    }

    private void saveDeployment(String owner, String repo, String environment, String sha, String msg, String author, Integer diffFiles, String deployedAt) {
        try {
            java.time.LocalDateTime dt = java.time.OffsetDateTime.parse(deployedAt).toLocalDateTime();
            
            Deployment deployment = Deployment.builder()
                .owner(owner)
                .repo(repo)
                .environment(environment)
                .sha(sha)
                .commitMessage(msg)
                .author(author)
                .diffFiles(diffFiles)
                .status("success")
                .deployedAt(dt)
                .build();
            
            deploymentRepository.save(deployment);
        } catch (Exception e) {
            log.warn("Could not save deployment to DB: {}", e.getMessage());
        }
    }

    private Map<String, Object> getFallbackStatus(String owner, String repo) {
        // Just return the latest workflow run data if deployment API fails or is empty
        List<Map<String, Object>> runs = getWorkflowRuns(owner, repo);
        if (runs.isEmpty()) return new HashMap<>();
        
        Map<String, Object> latest = runs.get(0);
        Map<String, Object> result = new HashMap<>();
        result.put("sha", latest.get("sha"));
        result.put("msg", latest.get("commitMsg"));
        result.put("author", latest.get("owner"));
        result.put("deployedAt", latest.get("time"));
        result.put("diffFiles", 5); // Fallback
        result.put("queueDepth", 2); // Fallback
        result.put("successRate", "95%");
        result.put("avgBuild", "5m");
        result.put("mttr", "15m");
        return result;
    }

    public List<Map<String, Object>> getIntegrations() {
        User user = getCurrentUser();
        return integrationRepository.findAllByUser(user).stream()
            .map(i -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", i.getType().toLowerCase());
                map.put("name", i.getName());
                map.put("status", i.getStatus());
                map.put("webhookUrl", i.getWebhookUrl());
                map.put("success", i.getSuccessCount());
                map.put("lastFired", i.getLastFired() != null ? "Just now" : "Never");
                return map;
            }).toList();
    }

    public List<Map<String, Object>> getRunners() {
        try {
            syncRunners();
        } catch (Exception e) {
            log.error("Failed to sync runners: {}", e.getMessage());
        }
        
        return runnerRepository.findAll().stream()
            .map(r -> {
                Map<String, Object> map = new HashMap<>();
                map.put("name", r.getName());
                map.put("jobs", r.getJobs());
                map.put("pct", r.getLoadPct());
                map.put("status", r.getStatus());
                return map;
            }).toList();
    }

    public List<Map<String, Object>> getAuditLogs() {
        try {
            syncEvents();
        } catch (Exception e) {
            log.error("Failed to sync events: {}", e.getMessage());
        }

        return auditLogRepository.findAllByOrderByCreatedAtDesc().stream()
            .limit(10)
            .map(auditLog -> {
                Map<String, Object> entry = new HashMap<>();
                entry.put("initials", auditLog.getInitials());
                entry.put("user", auditLog.getUserName());
                entry.put("action", auditLog.getAction());
                entry.put("target", auditLog.getTarget());
                entry.put("ago", auditLog.getAgo());
                entry.put("date", auditLog.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, h:mm a")));
                entry.put("color", auditLog.getColor());
                entry.put("avatar", auditLog.getAvatarUrl());
                return entry;
            }).toList();
    }

    private void syncRunners() {
        String token = getGithubTokenForCurrentUser();
        // For demo, we'll fetch from a known repo or the user's first repo
        List<Map<String, Object>> repos = getUserRepositories();
        if (repos.isEmpty()) return;
        
        String fullRepoName = (String) repos.get(0).get("full_name");
        String url = GITHUB_API_URL + "/repos/" + fullRepoName + "/actions/runners";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        
        try {
            Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
            List<Map<String, Object>> runners = (List<Map<String, Object>>) response.get("runners");
            
            for (Map<String, Object> r : runners) {
                String name = (String) r.get("name");
                String status = "online".equals(r.get("status")) ? "active" : "offline";
                
                Runner runner = runnerRepository.findByName(name)
                    .orElse(Runner.builder().name(name).build());
                
                runner.setStatus(status);
                // GitHub API doesn't give loadPct directly, we can simulate or fetch from elsewhere
                runner.setLoadPct(status.equals("active") ? (int)(Math.random() * 40 + 20) : 0);
                runner.setJobs(status.equals("active") ? (int)(Math.random() * 3 + 1) : 0);
                runnerRepository.save(runner);
            }
        } catch (Exception e) {
            log.warn("Could not fetch real runners (maybe insufficient permissions): {}", e.getMessage());
        }
    }

    private void syncEvents() {
        User user = getCurrentUser();
        String token = user.getGithubAccessToken();
        List<Map<String, Object>> repos = getUserRepositories();
        if (repos.isEmpty()) return;

        String fullRepoName = (String) repos.get(0).get("full_name");
        String url = GITHUB_API_URL + "/repos/" + fullRepoName + "/events";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");

        try {
            List<Map<String, Object>> events = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), List.class).getBody();
            if (events == null) return;

            for (Map<String, Object> event : events.stream().limit(10).toList()) {
                String type = (String) event.get("type");
                String action = "Activity";
                String target = fullRepoName;
                
                String createdAtStr = (String) event.get("created_at");
                java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(createdAtStr);
                java.time.LocalDateTime eventTime = odt.toLocalDateTime();

                if ("PushEvent".equals(type)) {
                    action = "Pushed code";
                    target = "to master";
                } else if ("PullRequestEvent".equals(type)) {
                    Map<String, Object> payload = (Map<String, Object>) event.get("payload");
                    String prAction = (String) payload.get("action");
                    Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
                    
                    if ("closed".equals(prAction) && Boolean.TRUE.equals(pr.get("merged"))) {
                        action = "PR merged";
                        String mergedAt = (String) pr.get("merged_at");
                        if (mergedAt != null) {
                            eventTime = java.time.OffsetDateTime.parse(mergedAt).toLocalDateTime();
                        }
                    } else {
                        action = "PR " + prAction;
                    }
                    target = "#" + pr.get("number");
                }

                Map<String, Object> actor = (Map<String, Object>) event.get("actor");
                String login = (String) actor.get("login");
                String avatar = (String) actor.get("avatar_url");

                AuditLog auditLog = AuditLog.builder()
                    .initials(login.substring(0, Math.min(2, login.length())).toUpperCase())
                    .userName(login)
                    .action(action)
                    .target(target)
                    .createdAt(eventTime)
                    .color("#0061AA")
                    .avatarUrl(avatar)
                    .build();
                
                // Deduplication based on action, target and exact time
                final String finalAction = action;
                final String finalTarget = target;
                final java.time.LocalDateTime finalTime = eventTime;
                if (auditLogRepository.findAll().stream().noneMatch(l -> 
                    l.getAction().equals(finalAction) && 
                    l.getTarget().equals(finalTarget) && 
                    (l.getCreatedAt() != null && l.getCreatedAt().equals(finalTime)))) {
                    auditLogRepository.save(auditLog);
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch real events: {}", e.getMessage());
        }
    }

    public void logAction(String action, String target, User user) {
        String initials = user.getFullName().substring(0, 1).toUpperCase();
        if (user.getFullName().contains(" ")) {
            initials += user.getFullName().split(" ")[1].substring(0, 1).toUpperCase();
        }

        AuditLog auditLog = AuditLog.builder()
            .action(action)
            .target(target)
            .userName(user.getFullName())
            .initials(initials)
            .color("#0061AA")
            .createdAt(java.time.LocalDateTime.now())
            .avatarUrl(user.getAvatarUrl())
            .build();
        
        auditLogRepository.save(auditLog);
    }

    private Map<String, Object> createAuditEntry(String initials, String action, String target, String ago, String color, String avatar) {
        Map<String, Object> entry = new java.util.HashMap<>();
        entry.put("initials", initials);
        entry.put("action", action);
        entry.put("target", target);
        entry.put("ago", ago);
        entry.put("color", color);
        entry.put("avatar", avatar);
        return entry;
    }

    private Map<String, Object> createIntegration(String name, String lastFired, int success, String status) {
        Map<String, Object> integration = new java.util.HashMap<>();
        integration.put("name", name);
        integration.put("lastFired", lastFired);
        integration.put("success", success);
        integration.put("status", status);
        return integration;
    }

    private Map<String, Object> createRunner(String name, int jobs, int pct, String status) {
        int jitter = (int) (Math.random() * 15) - 7;
        int finalPct = Math.max(0, Math.min(100, pct + jitter));
        int finalJobs = finalPct > 0 ? Math.max(1, (int)Math.ceil(jobs * (finalPct / 100.0) * 1.5)) : 0;

        Map<String, Object> runner = new java.util.HashMap<>();
        runner.put("name", name);
        runner.put("jobs", finalJobs);
        runner.put("pct", finalPct);
        runner.put("status", finalPct == 0 && Math.random() > 0.8 ? "offline" : status);
        return runner;
    }

    public Map<String, Object> getWorkflowRunJobs(String owner, String repo, Long runId) {
        Map<String, Object> response = getWorkflowRunJobsRaw(owner, repo, runId);
        List<Map<String, Object>> jobs = (List<Map<String, Object>>) response.get("jobs");
        
        List<Map<String, Object>> mappedStages = jobs.stream().map(job -> {
            String status = (String) job.get("status");
            String conclusion = (String) job.get("conclusion");
            
            String mappedStatus = "pending";
            if ("completed".equals(status)) {
                mappedStatus = "success".equals(conclusion) ? "success" : "failed";
            } else if ("in_progress".equals(status)) {
                mappedStatus = "running";
            } else if ("queued".equals(status)) {
                mappedStatus = "queued";
            }

            return Map.of(
                "id", job.get("id"),
                "name", job.get("name"),
                "status", mappedStatus,
                "duration", 45 
            );
        }).toList();

        return Map.of("stages", mappedStages);
    }

    private Map<String, Object> getWorkflowRunJobsRaw(String owner, String repo, Long runId) {
        String token = getGithubTokenForCurrentUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/jobs",
                HttpMethod.GET,
                entity,
                Map.class
        );

        return response.getBody();
    }

    public String getFailedJobLogs(String owner, String repo, Long runId) {
        try {
            Map<String, Object> jobsResponse = getWorkflowRunJobsRaw(owner, repo, runId);
            List<Map<String, Object>> jobs = (List<Map<String, Object>>) jobsResponse.get("jobs");
            
            Optional<Map<String, Object>> failedJob = jobs.stream()
                .filter(job -> "failure".equals(job.get("conclusion")))
                .findFirst();
            
            if (failedJob.isPresent()) {
                Long jobId = ((Number) failedJob.get().get("id")).longValue();
                String fullLogs = getJobLogs(owner, repo, jobId);
                return extractLogSnippet(fullLogs);
            }
        } catch (Exception e) {
            log.warn("Could not fetch failed job logs for run {}: {}", runId, e.getMessage());
        }
        return null;
    }

    private String extractLogSnippet(String logs) {
        if (logs == null || logs.isBlank()) return "No logs available.";
        
        String[] lines = logs.split("\n");
        java.util.List<String> relevantLines = new java.util.ArrayList<>();
        
        // Find lines containing "Error", "Failed", "Exception" or just take the last 15 lines
        for (int i = Math.max(0, lines.length - 50); i < lines.length; i++) {
            String line = lines[i];
            if (line.toLowerCase().contains("error") || 
                line.toLowerCase().contains("failed") || 
                line.toLowerCase().contains("exception") ||
                i >= lines.length - 15) {
                relevantLines.add(line);
            }
        }
        
        // Limit snippet to ~15-20 lines
        int start = Math.max(0, relevantLines.size() - 20);
        return String.join("\n", relevantLines.subList(start, relevantLines.size()));
    }

    private String getGithubTokenForCurrentUser() {
        return getCurrentUser().getGithubAccessToken();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<Map<String, Object>> getTeam() {
        return userRepository.findAll().stream().map(user -> {
            Map<String, Object> member = new HashMap<>();
            member.put("id", user.getId().toString());
            member.put("name", user.getFullName());
            member.put("email", user.getEmail());
            member.put("role", capitalize(user.getRole().name()));
            member.put("status", "Active"); // Defaulting to Active
            
            // Derive last active from updatedAt
            if (user.getUpdatedAt() != null) {
                member.put("lastActive", timeAgo(user.getUpdatedAt()));
            } else {
                member.put("lastActive", "Recently");
            }
            
            // Map real teams
            List<String> userTeams = user.getTeams().stream()
                    .map(Team::getName)
                    .toList();
            member.put("teams", userTeams.isEmpty() ? List.of("Unassigned") : userTeams);
            
            member.put("avatar", getInitials(user.getFullName()));
            member.put("avatarUrl", user.getAvatarUrl());
            
            // Format joined date
            if (user.getCreatedAt() != null) {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy");
                member.put("joinedDate", user.getCreatedAt().format(formatter));
            } else {
                member.put("joinedDate", "Recently");
            }
            
            // Assign a color based on name
            String[] colors = {"bg-blue-50 text-blue-600 border-blue-100", 
                             "bg-emerald-50 text-emerald-600 border-emerald-100", 
                             "bg-purple-50 text-purple-600 border-purple-100", 
                             "bg-amber-50 text-amber-600 border-amber-100"};
            int colorIdx = Math.abs(user.getFullName().hashCode() % colors.length);
            member.put("avatarBg", colors[colorIdx]);
            
            return member;
        }).toList();
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "U";
        String[] parts = name.split(" ");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private String timeAgo(java.time.LocalDateTime dateTime) {
        java.time.Duration duration = java.time.Duration.between(dateTime, java.time.LocalDateTime.now());
        long seconds = duration.getSeconds();
        if (seconds < 60) return "Just now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }

    public List<Map<String, Object>> getAllTeams() {
        return teamRepository.findAll().stream()
                .map(this::mapTeam)
                .toList();
    }

    public Map<String, Object> createTeam(Map<String, String> data) {
        User creator = getCurrentUser();
        Team team = Team.builder()
                .name(data.get("name"))
                .description(data.get("description"))
                .avatarUrl(data.get("avatarUrl"))
                .coverImageUrl(data.get("coverImageUrl"))
                .createdBy(creator)
                .build();
        Team saved = teamRepository.save(team);
        log.info("Created new team: {} by user: {}", saved.getName(), creator.getEmail());
        return mapTeam(saved);
    }

    public void deleteTeam(Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Team not found"));
        
        User currentUser = getCurrentUser();
        
        // Only creator can delete
        if (team.getCreatedBy() != null && !team.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Only the team creator can delete this team");
        }
        
        // Remove team association from users first to avoid constraint violations if necessary
        // In this case, team is the inverse side (mappedBy), so JPA should handle it
        teamRepository.delete(team);
        log.info("Deleted team: {} by user: {}", id, currentUser.getEmail());
    }

    public Map<String, Object> getTeamDetails(Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Team not found"));
        
        Map<String, Object> details = mapTeam(team);
        
        // Add detailed member info
        List<Map<String, Object>> members = team.getMembers().stream()
                .map(user -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", user.getId().toString());
                    m.put("name", user.getFullName());
                    m.put("email", user.getEmail());
                    m.put("avatarUrl", user.getAvatarUrl());
                    m.put("role", capitalize(user.getRole().name()));
                    return m;
                }).toList();
        
        details.put("members", members);
        return details;
    }

    public void updateUserTeams(Long userId, List<Long> teamIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<Team> teams = teamRepository.findAllById(teamIds);
        user.getTeams().clear();
        user.getTeams().addAll(teams);
        userRepository.save(user);
        log.info("Updated teams for user {}: {}", userId, teamIds);
    }

    private Map<String, Object> mapTeam(Team team) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", team.getId());
        map.put("name", team.getName());
        map.put("description", team.getDescription());
        map.put("avatarUrl", team.getAvatarUrl());
        map.put("coverImageUrl", team.getCoverImageUrl());
        map.put("memberCount", team.getMembers().size());
        map.put("createdAt", team.getCreatedAt());
        
        if (team.getCreatedBy() != null) {
            map.put("createdById", team.getCreatedBy().getId().toString());
            map.put("creatorName", team.getCreatedBy().getFullName());
        }
        
        return map;
    }
}
