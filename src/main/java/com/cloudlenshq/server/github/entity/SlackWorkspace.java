package com.cloudlenshq.server.github.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "slack_workspaces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlackWorkspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String teamId;

    private String teamName;

    @Column(nullable = false, length = 1000)
    private String botToken;

    private String botUserId;

    private String authedUserId;

    private String defaultChannel;

    private String webhookUrl;

    private LocalDateTime installedAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private com.cloudlenshq.server.auth.entity.User user;
}
