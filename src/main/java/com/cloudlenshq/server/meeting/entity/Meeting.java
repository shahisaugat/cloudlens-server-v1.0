package com.cloudlenshq.server.meeting.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meetings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String meetingId; // e.g. "mock-1" or dynamic
    private String title;
    private String date; // YYYY-MM-DD
    private String time; // HH:MM
    private String roomId; // huddle-xyz

    private String hostEmail;
    private String hostName;

    @JsonProperty("isMutedAll")
    private boolean isMutedAll;

    @JsonProperty("isCamDisabled")
    private boolean isCamDisabled;

    @JsonProperty("isLocked")
    private boolean isLocked;

    @JsonProperty("isRecording")
    private boolean isRecording;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "meeting_attendees", joinColumns = @JoinColumn(name = "meeting_id"))
    @Column(name = "attendee_email")
    @Builder.Default
    private List<String> attendees = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "meeting_active_participants", joinColumns = @JoinColumn(name = "meeting_id"))
    @Builder.Default
    private List<ParticipantStatus> activeParticipants = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "meeting_join_requests", joinColumns = @JoinColumn(name = "meeting_id"))
    @Builder.Default
    private List<JoinRequest> joinRequests = new ArrayList<>();
}
