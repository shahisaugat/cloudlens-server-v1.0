package com.cloudlenshq.server.meeting.repository;

import com.cloudlenshq.server.meeting.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    Optional<Meeting> findByMeetingId(String meetingId);
    Optional<Meeting> findByRoomId(String roomId);
}
