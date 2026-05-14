package com.cloudlenshq.server.github.entity;

import com.cloudlenshq.server.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "custom_integrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomIntegration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String uniqueId; // The ID used in the webhook URL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String name;
    private String type; // WEBHOOK, POLLING, OAUTH
    private String category; // Monitoring, CI/CD, etc.
    
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String configJson; // Stores auth, polling, or mapping config

    private String status; // active, paused, error
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "active";
    }
}
