package com.example.teammeeting.availability;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
  List<Availability> findAllByMemberId(Long memberId);

  List<Availability> findAllByMemberMeetingId(Long meetingId);

  void deleteAllByMemberId(Long memberId);
}
