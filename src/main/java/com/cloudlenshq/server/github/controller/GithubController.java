package com.cloudlenshq.server.github.controller;

import com.cloudlenshq.server.github.service.GithubService;
import com.cloudlenshq.server.github.service.SlackService;
import com.cloudlenshq.server.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class GithubController {

    private final GithubService githubService;
    private final SlackService slackService;

    @PostMapping("/integrations/webhook")
    public ResponseEntity<Void> saveWebhookConfig(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        String type = body.get("type");
        String webhookUrl = body.get("webhookUrl");
        githubService.updateIntegrationWebhook(user, type, webhookUrl);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/repos")
    public ResponseEntity<List<Map<String, Object>>> getRepositories() {
        log.info("Fetching repositories for current user");
        List<Map<String, Object>> repos = githubService.getUserRepositories();
        log.info("Found {} repositories", repos.size());
        return ResponseEntity.ok(repos);
    }

    @GetMapping("/runners")
    public ResponseEntity<List<Map<String, Object>>> getRunners() {
        log.info("Fetching GitHub runners");
        return ResponseEntity.ok(githubService.getRunners());
    }

    @GetMapping("/deployments")
    public ResponseEntity<List<Map<String, Object>>> getDeployments() {
        return ResponseEntity.ok(githubService.getDeployments());
    }

    @GetMapping("/integrations")
    public ResponseEntity<List<Map<String, Object>>> getIntegrations() {
        return ResponseEntity.ok(githubService.getIntegrations());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<Map<String, Object>>> getAuditLogs() {
        return ResponseEntity.ok(githubService.getAuditLogs());
    }

    @GetMapping("/repos/{owner}/{repo:.+}/pipelines")
    public ResponseEntity<List<Map<String, Object>>> getPipelines(
            @PathVariable String owner,
            @PathVariable String repo) {
        log.info("Fetching pipelines for {}/{}", owner, repo);
        return ResponseEntity.ok(githubService.getWorkflowRuns(owner, repo));
    }

    @GetMapping("/repos/{owner}/{repo:.+}/environments/{environment}")
    public ResponseEntity<Map<String, Object>> getEnvironmentStatus(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String environment) {
        return ResponseEntity.ok(githubService.getEnvironmentStatus(owner, repo, environment));
    }

    @GetMapping("/repos/{owner}/{repo:.+}/pipelines/{runId}")
    public ResponseEntity<Map<String, Object>> getPipelineDetails(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Long runId) {
        return ResponseEntity.ok(githubService.getWorkflowRunJobs(owner, repo, runId));
    }

    @GetMapping("/repos/{owner}/{repo:.+}/jobs/{jobId}/logs")
    public ResponseEntity<String> getJobLogs(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Long jobId) {
        return ResponseEntity.ok(githubService.getJobLogs(owner, repo, jobId));
    }

    @PostMapping("/repos/{owner}/{repo:.+}/pipelines/{runId}/rerun")
    public ResponseEntity<Void> rerunPipeline(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Long runId) {
        githubService.rerunWorkflow(owner, repo, runId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/repos/{owner}/{repo:.+}/workflows")
    public ResponseEntity<List<Map<String, Object>>> getWorkflows(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(githubService.getWorkflows(owner, repo));
    }

    @GetMapping("/repos/{owner}/{repo:.+}/branches")
    public ResponseEntity<List<Map<String, Object>>> getBranches(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(githubService.getBranches(owner, repo));
    }

    @PostMapping("/repos/{owner}/{repo:.+}/deploy")
    public ResponseEntity<Void> triggerDeployment(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody Map<String, String> body) {
        String workflowId = body.get("workflowId");
        String ref = body.get("ref");
        githubService.triggerWorkflow(owner, repo, workflowId, ref);
        return ResponseEntity.ok().build();
    }
    @GetMapping("/team")
    public ResponseEntity<List<Map<String, Object>>> getTeam() {
        return ResponseEntity.ok(githubService.getTeam());
    }
    @GetMapping("/teams")
    public ResponseEntity<List<Map<String, Object>>> getAllTeams() {
        return ResponseEntity.ok(githubService.getAllTeams());
    }

    @PostMapping("/teams")
    public ResponseEntity<Map<String, Object>> createTeam(@RequestBody Map<String, String> data) {
        return ResponseEntity.ok(githubService.createTeam(data));
    }

    @GetMapping("/teams/{id}")
    public ResponseEntity<Map<String, Object>> getTeamDetails(@PathVariable Long id) {
        return ResponseEntity.ok(githubService.getTeamDetails(id));
    }

    @DeleteMapping("/teams/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        githubService.deleteTeam(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/team/assign")
    public ResponseEntity<Void> assignTeams(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        List<Long> teamIds = ((List<?>) body.get("teamIds")).stream()
                .map(id -> Long.valueOf(id.toString()))
                .toList();
        githubService.updateUserTeams(userId, teamIds);
        return ResponseEntity.ok().build();
    }
}
