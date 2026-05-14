package com.cloudlenshq.server.github.entity;

import com.cloudlenshq.server.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "incidents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String incidentId; // e.g., INC-4921
    private String severity; // critical, warning, resolved
    private String status; // Investigating, Identified, Monitoring, Resolved
    private String service;
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private LocalDateTime startedAt;
    private String owner;
    private String impact;
    private boolean acknowledged;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (incidentId == null) {
            incidentId = "INC-" + (int)(Math.random() * 9000 + 1000);
        }
    }
}
