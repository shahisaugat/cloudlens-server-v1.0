package com.cloudlenshq.server.github.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_payloads")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id")
    private CustomIntegration integration;

    @Column(columnDefinition = "TEXT")
    private String payload; // Raw JSON

    private String sourceIp;
    private int statusCode;
    private LocalDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        receivedAt = LocalDateTime.now();
    }
}
