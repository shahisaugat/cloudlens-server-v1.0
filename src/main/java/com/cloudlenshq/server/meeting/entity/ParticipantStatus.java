package com.cloudlenshq.server.meeting.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantStatus {
    private String email;
    private String fullName;
    private boolean micOn;
    private boolean cameraOn;
}
