package com.cloudlenshq.server.github.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String initials;
    private String action;
    private String target;
    private String ago; // For demo, we can store string or calculate
    private String color;
    private String userName;
    private String avatarUrl;

    private LocalDateTime createdAt;
}
