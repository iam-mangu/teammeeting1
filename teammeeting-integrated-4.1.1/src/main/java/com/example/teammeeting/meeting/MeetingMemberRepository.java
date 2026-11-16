package com.example.teammeeting.meeting;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface MeetingMemberRepository extends JpaRepository<MeetingMember, Long> {
  Optional<MeetingMember> findByMeetingIdAndUserId(Long meetingId, Long userId);

  List<MeetingMember> findAllByMeetingIdOrderByIdAsc(Long meetingId);

  List<MeetingMember> findAllByUserIdOrderByIdDesc(Long userId);
}
