package com.cloudlenshq.server.meeting.controller;

import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.auth.repository.UserRepository;
import com.cloudlenshq.server.meeting.entity.Meeting;
import com.cloudlenshq.server.meeting.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class MeetingController {
    private final MeetingService meetingService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Meeting>> getAllMeetings() {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(meetingService.getMeetingsForUser(email));
    }

    @GetMapping("/{meetingId}")
    public ResponseEntity<Meeting> getMeetingByMeetingId(@PathVariable String meetingId) {
        return ResponseEntity.ok(meetingService.getMeetingByMeetingId(meetingId));
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<Meeting> getMeetingByRoomId(@PathVariable String roomId) {
        Meeting m = meetingService.getMeetingByRoomId(roomId);
        if (m == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(m);
    }

    @PostMapping
    public ResponseEntity<Meeting> createOrUpdateMeeting(@RequestBody Meeting meeting) {
        return ResponseEntity.ok(meetingService.createOrUpdateMeeting(meeting));
    }

    @PutMapping("/{meetingId}/controls")
    public ResponseEntity<Meeting> updateMeetingControls(
            @PathVariable String meetingId,
            @RequestBody Meeting updatedControls) {
        return ResponseEntity.ok(meetingService.updateMeetingControls(meetingId, updatedControls));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable Long id) {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        Meeting meeting = meetingService.getAllMeetings().stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (meeting.getHostEmail() != null && !meeting.getHostEmail().isEmpty() 
                && !meeting.getHostEmail().equalsIgnoreCase(email)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        
        meetingService.deleteMeeting(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/room/{roomId}/join")
    public ResponseEntity<Meeting> joinRoom(
            @PathVariable String roomId,
            @RequestParam boolean micOn,
            @RequestParam boolean cameraOn) {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        String fullName = userRepository.findByEmail(email)
                .map(User::getFullName)
                .orElse(email.split("@")[0]);
        return ResponseEntity.ok(meetingService.joinMeetingRoom(roomId, email, fullName, micOn, cameraOn));
    }

    @PostMapping("/room/{roomId}/leave")
    public ResponseEntity<Meeting> leaveRoom(@PathVariable String roomId) {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(meetingService.leaveMeetingRoom(roomId, email));
    }

    @PutMapping("/room/{roomId}/participant-status")
    public ResponseEntity<Meeting> updateStatus(
            @PathVariable String roomId,
            @RequestParam boolean micOn,
            @RequestParam boolean cameraOn) {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(meetingService.updateParticipantStatus(roomId, email, micOn, cameraOn));
    }

    // ─── Join Request Endpoints ───────────────────────────────────────────

    @PostMapping("/room/{roomId}/request-join")
    public ResponseEntity<com.cloudlenshq.server.meeting.entity.JoinRequest> requestToJoin(@PathVariable String roomId) {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        String fullName = userRepository.findByEmail(email)
                .map(User::getFullName)
                .orElse(email.split("@")[0]);
        return ResponseEntity.ok(meetingService.requestToJoin(roomId, email, fullName));
    }

    @PutMapping("/room/{roomId}/approve-join")
    public ResponseEntity<Meeting> approveJoin(
            @PathVariable String roomId,
            @RequestParam String email) {
        String hostEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(meetingService.approveJoinRequest(roomId, email, hostEmail));
    }

    @PutMapping("/room/{roomId}/deny-join")
    public ResponseEntity<Meeting> denyJoin(
            @PathVariable String roomId,
            @RequestParam String email) {
        String hostEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(meetingService.denyJoinRequest(roomId, email, hostEmail));
    }

    @GetMapping("/room/{roomId}/my-request-status")
    public ResponseEntity<java.util.Map<String, String>> myRequestStatus(@PathVariable String roomId) {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        String status = meetingService.getJoinRequestStatus(roomId, email);
        return ResponseEntity.ok(java.util.Map.of("status", status));
    }
}
