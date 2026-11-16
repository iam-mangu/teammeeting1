package com.example.teammeeting.meeting;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface MeetingDateRepository extends JpaRepository<MeetingDate, Long> {
  List<MeetingDate> findAllByMeetingIdOrderByDateAsc(Long meetingId);
}
