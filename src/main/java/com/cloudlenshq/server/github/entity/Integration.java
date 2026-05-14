package com.cloudlenshq.server.github.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "integrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Integration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // SLACK, GITHUB, PAGERDUTY

    private String status; // active, connected, error
    
    @Column(length = 1000)
    private String webhookUrl;

    private LocalDateTime lastFired;
    @Builder.Default
    private Integer successCount = 0;

    @ManyToOne
    @JoinColumn(name = "slack_workspace_id")
    private SlackWorkspace slackWorkspace;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private com.cloudlenshq.server.auth.entity.User user;
}
