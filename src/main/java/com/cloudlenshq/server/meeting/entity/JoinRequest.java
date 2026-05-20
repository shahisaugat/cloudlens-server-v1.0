package com.cloudlenshq.server.meeting.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinRequest {
    private String email;
    private String fullName;
    private String status; // PENDING, APPROVED, DENIED
    private String requestedAt;
}
