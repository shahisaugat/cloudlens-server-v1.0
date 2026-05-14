package com.cloudlenshq.server.github.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "runners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Runner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String status; // active, offline
    private Integer jobs;
    private Integer loadPct;
    private String type; // 2xlarge, xlarge, large
}
