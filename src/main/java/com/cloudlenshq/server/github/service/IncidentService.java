package com.cloudlenshq.server.github.service;

import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.github.entity.Incident;
import com.cloudlenshq.server.github.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentService {
    private final IncidentRepository incidentRepository;

    public List<Incident> getUserIncidents(User user) {
        return incidentRepository.findAllByUserOrderByStartedAtDesc(user);
    }

    @Transactional
    public Incident createIncident(Incident incident, User user) {
        incident.setUser(user);
        return incidentRepository.save(incident);
    }

    @Transactional
    public Incident acknowledgeIncident(Long id, User user) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found"));
        
        // Basic security check
        if (!incident.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        incident.setAcknowledged(true);
        return incidentRepository.save(incident);
    }

    @Transactional
    public void resolveIncident(Long id, User user) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found"));
        
        if (!incident.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        incident.setStatus("Resolved");
        incident.setSeverity("resolved");
        incidentRepository.save(incident);
    }
}
