package com.cloudlenshq.server.github.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "custom_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id")
    private CustomIntegration integration;

    private String conditionField; // e.g. status, priority
    private String conditionValue; // e.g. failed, critical
    private String actionType; // e.g. INCIDENT, AUDIT_LOG
    
    @Column(columnDefinition = "TEXT")
    private String actionConfig; // JSON config for the action
}
