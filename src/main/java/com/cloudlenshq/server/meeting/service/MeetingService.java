package com.cloudlenshq.server.meeting.service;

import com.cloudlenshq.server.meeting.entity.Meeting;
import com.cloudlenshq.server.meeting.entity.JoinRequest;
import com.cloudlenshq.server.meeting.entity.ParticipantStatus;
import com.cloudlenshq.server.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeetingService {
    private final MeetingRepository meetingRepository;

    public List<Meeting> getAllMeetings() {
        return meetingRepository.findAll();
    }

    public List<Meeting> getMeetingsForUser(String email) {
        return meetingRepository.findAll().stream()
                .filter(m -> m.getHostEmail() == null || m.getHostEmail().isEmpty() 
                        || m.getHostEmail().equalsIgnoreCase(email) 
                        || m.getAttendees().stream().anyMatch(email::equalsIgnoreCase))
                .collect(java.util.stream.Collectors.toList());
    }

    public Meeting getMeetingByMeetingId(String meetingId) {
        return meetingRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found with ID: " + meetingId));
    }

    public Meeting getMeetingByRoomId(String roomId) {
        return meetingRepository.findByRoomId(roomId)
                .orElse(null);
    }

    public Meeting createOrUpdateMeeting(Meeting meeting) {
        String currentUserEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();

        if (meeting.getMeetingId() == null || meeting.getMeetingId().isEmpty()) {
            meeting.setMeetingId("meet-" + (int)(Math.random() * 900000 + 100000));
        }
        if (meeting.getRoomId() == null || meeting.getRoomId().isEmpty()) {
            meeting.setRoomId("huddle-" + (int)(Math.random() * 900000 + 100000));
        }
        // If an existing meeting has the same meetingId or roomId, merge/update it
        return meetingRepository.findByMeetingId(meeting.getMeetingId())
                .map(existing -> {
                    // Security Check: Only the host can update an existing meeting
                    if (existing.getHostEmail() != null && !existing.getHostEmail().isEmpty() 
                            && !existing.getHostEmail().equalsIgnoreCase(currentUserEmail)) {
                        throw new RuntimeException("Access Denied: Only the host can modify this meeting.");
                    }
                    existing.setTitle(meeting.getTitle());
                    existing.setDate(meeting.getDate());
                    existing.setTime(meeting.getTime());
                    existing.setAttendees(meeting.getAttendees());
                    existing.setHostEmail(meeting.getHostEmail());
                    existing.setHostName(meeting.getHostName());
                    existing.setMutedAll(meeting.isMutedAll());
                    existing.setCamDisabled(meeting.isCamDisabled());
                    existing.setLocked(meeting.isLocked());
                    existing.setRecording(meeting.isRecording());
                    return meetingRepository.save(existing);
                })
                .orElseGet(() -> {
                    if (meeting.getHostEmail() == null || meeting.getHostEmail().isEmpty()) {
                        meeting.setHostEmail(currentUserEmail);
                    }
                    return meetingRepository.save(meeting);
                });
    }

    public Meeting updateMeetingControls(String meetingId, Meeting updatedControls) {
        String currentUserEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        Meeting existing = getMeetingByMeetingId(meetingId);
        
        // Security Check: Only the host can modify meeting controls
        if (existing.getHostEmail() != null && !existing.getHostEmail().isEmpty() 
                && !existing.getHostEmail().equalsIgnoreCase(currentUserEmail)) {
            throw new RuntimeException("Access Denied: Only the host can modify meeting controls.");
        }

        existing.setMutedAll(updatedControls.isMutedAll());
        existing.setCamDisabled(updatedControls.isCamDisabled());
        existing.setLocked(updatedControls.isLocked());
        existing.setRecording(updatedControls.isRecording());
        return meetingRepository.save(existing);
    }

    public void deleteMeeting(Long id) {
        meetingRepository.deleteById(id);
    }

    /**
     * Check if a user is authorized to join a meeting room.
     * Authorized = host OR in attendees list OR has an APPROVED join request.
     */
    private boolean isUserAuthorized(Meeting meeting, String email) {
        if (meeting.getHostEmail() != null && meeting.getHostEmail().equalsIgnoreCase(email)) {
            return true;
        }
        if (meeting.getAttendees().stream().anyMatch(a -> a.equalsIgnoreCase(email))) {
            return true;
        }
        return meeting.getJoinRequests().stream()
                .anyMatch(r -> r.getEmail().equalsIgnoreCase(email) && "APPROVED".equals(r.getStatus()));
    }

    public Meeting joinMeetingRoom(String roomId, String email, String fullName, boolean micOn, boolean cameraOn) {
        Meeting meeting = getMeetingByRoomId(roomId);
        if (meeting == null) {
            throw new RuntimeException("Meeting room not found");
        }
        
        // Authorization gate: if meeting is locked, verify access
        if (meeting.isLocked() && !isUserAuthorized(meeting, email)) {
            throw new RuntimeException("ACCESS_DENIED: You are not authorized to join this locked meeting.");
        }

        // Remove caller's own stale entry before adding current status
        meeting.getActiveParticipants().removeIf(p -> p.getEmail().equalsIgnoreCase(email));
        
        ParticipantStatus status = ParticipantStatus.builder()
                .email(email)
                .fullName(fullName)
                .micOn(micOn)
                .cameraOn(cameraOn)
                .build();
        
        meeting.getActiveParticipants().add(status);
        return meetingRepository.save(meeting);
    }

    public Meeting leaveMeetingRoom(String roomId, String email) {
        Meeting meeting = getMeetingByRoomId(roomId);
        if (meeting != null) {
            meeting.getActiveParticipants().removeIf(p -> p.getEmail().equalsIgnoreCase(email));
            return meetingRepository.save(meeting);
        }
        return null;
    }

    public Meeting updateParticipantStatus(String roomId, String email, boolean micOn, boolean cameraOn) {
        Meeting meeting = getMeetingByRoomId(roomId);
        if (meeting != null) {
            meeting.getActiveParticipants().stream()
                    .filter(p -> p.getEmail().equalsIgnoreCase(email))
                    .findFirst()
                    .ifPresent(p -> {
                        p.setMicOn(micOn);
                        p.setCameraOn(cameraOn);
                    });
            return meetingRepository.save(meeting);
        }
        return null;
    }

    // ─── Join Request Lifecycle ────────────────────────────────────────────

    public JoinRequest requestToJoin(String roomId, String email, String fullName) {
        Meeting meeting = getMeetingByRoomId(roomId);
        if (meeting == null) {
            throw new RuntimeException("Meeting room not found");
        }

        // If already authorized, no request needed
        if (isUserAuthorized(meeting, email)) {
            return JoinRequest.builder().email(email).fullName(fullName).status("APPROVED").build();
        }

        // Check if a request already exists
        JoinRequest existing = meeting.getJoinRequests().stream()
                .filter(r -> r.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            return existing; // Return current status (PENDING or DENIED)
        }

        // Create new pending request
        JoinRequest request = JoinRequest.builder()
                .email(email)
                .fullName(fullName)
                .status("PENDING")
                .requestedAt(Instant.now().toString())
                .build();

        meeting.getJoinRequests().add(request);
        meetingRepository.save(meeting);
        return request;
    }

    public Meeting approveJoinRequest(String roomId, String requesterEmail, String hostEmail) {
        Meeting meeting = getMeetingByRoomId(roomId);
        if (meeting == null) {
            throw new RuntimeException("Meeting room not found");
        }

        // Only host can approve
        if (meeting.getHostEmail() == null || !meeting.getHostEmail().equalsIgnoreCase(hostEmail)) {
            throw new RuntimeException("Access Denied: Only the host can approve join requests.");
        }

        meeting.getJoinRequests().stream()
                .filter(r -> r.getEmail().equalsIgnoreCase(requesterEmail))
                .findFirst()
                .ifPresent(r -> {
                    r.setStatus("APPROVED");
                    // Also add to attendees so they stay authorized
                    if (meeting.getAttendees().stream().noneMatch(a -> a.equalsIgnoreCase(requesterEmail))) {
                        meeting.getAttendees().add(requesterEmail);
                    }
                });

        return meetingRepository.save(meeting);
    }

    public Meeting denyJoinRequest(String roomId, String requesterEmail, String hostEmail) {
        Meeting meeting = getMeetingByRoomId(roomId);
        if (meeting == null) {
            throw new RuntimeException("Meeting room not found");
        }

        // Only host can deny
        if (meeting.getHostEmail() == null || !meeting.getHostEmail().equalsIgnoreCase(hostEmail)) {
            throw new RuntimeException("Access Denied: Only the host can deny join requests.");
        }

        meeting.getJoinRequests().stream()
                .filter(r -> r.getEmail().equalsIgnoreCase(requesterEmail))
                .findFirst()
                .ifPresent(r -> r.setStatus("DENIED"));

        return meetingRepository.save(meeting);
    }

    public String getJoinRequestStatus(String roomId, String email) {
        Meeting meeting = getMeetingByRoomId(roomId);
        if (meeting == null) {
            return "NOT_FOUND";
        }

        // If already authorized (host, attendee), treat as approved
        if (isUserAuthorized(meeting, email)) {
            return "APPROVED";
        }

        return meeting.getJoinRequests().stream()
                .filter(r -> r.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .map(JoinRequest::getStatus)
                .orElse("NONE");
    }
}

