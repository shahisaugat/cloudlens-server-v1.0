package com.cloudlenshq.server.github.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "deployments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String owner;
    private String repo;
    private String environment;
    private String sha;
    private String commitMessage;
    private String author;
    private String pr;
    private Integer diffFiles;
    private String status;
    private LocalDateTime deployedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
