package com.cloudlenshq.server.github.controller;

import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.github.entity.Incident;
import com.cloudlenshq.server.github.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/github/incidents")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class IncidentController {
    private final IncidentService incidentService;

    @GetMapping
    public ResponseEntity<List<Incident>> getIncidents(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(incidentService.getUserIncidents(user));
    }

    @PostMapping
    public ResponseEntity<Incident> createIncident(
            @RequestBody Incident incident,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(incidentService.createIncident(incident, user));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Incident> acknowledgeIncident(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(incidentService.acknowledgeIncident(id, user));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveIncident(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        incidentService.resolveIncident(id, user);
        return ResponseEntity.ok().build();
    }
}
