package com.example.teammeeting.availability;

import com.example.teammeeting.common.*;
import com.example.teammeeting.meeting.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {
  private final MeetingAccess access;
  private final MeetingMemberRepository members;
  private final MeetingDateRepository dates;
  private final AvailabilityRepository availability;
  private final Clock clock;

  public AvailabilityService(
      MeetingAccess a,
      MeetingMemberRepository m,
      MeetingDateRepository d,
      AvailabilityRepository r,
      Clock c) {
    access = a;
    members = m;
    dates = d;
    availability = r;
    clock = c;
  }

  @Transactional
  public void save(Long id, String email, List<String> slots, boolean none) {
    access.requireMember(id, email, true);
    MeetingMember member =
        members.findByMeetingIdAndUserId(id, access.currentUser(email).getId()).orElseThrow();
    Map<Long, MeetingDate> allowed =
        dates.findAllByMeetingIdOrderByDateAsc(id).stream()
            .collect(Collectors.toMap(MeetingDate::getId, d -> d));
    List<Availability> replacements = new ArrayList<>();
    if (!none) {
      if (slots == null || slots.isEmpty() || slots.size() > 84)
        throw new BusinessException("가능한 시간을 선택하거나 가능 시간 없음을 눌러 주세요.");
      Set<String> seen = new HashSet<>();
      for (String slot : slots) {
        try {
          String[] parts = slot.split("_", -1);
          if (parts.length != 2) throw new IllegalArgumentException();
          Long dateId = Long.valueOf(parts[0]);
          int hour = Integer.parseInt(parts[1]);
          MeetingDate date = allowed.get(dateId);
          if (date == null || hour < 9 || hour > 20) throw new IllegalArgumentException();
          if (!seen.add(dateId + "_" + hour)) continue;
          Availability a = new Availability();
          a.setMember(member);
          a.setMeetingDate(date);
          a.setStartTime(LocalTime.of(hour, 0));
          replacements.add(a);
        } catch (Exception e) {
          throw new BusinessException("선택한 날짜 또는 시간이 올바르지 않습니다.");
        }
      }
    }
    availability.deleteAllByMemberId(member.getId());
    availability.flush();
    availability.saveAll(replacements);
    member.setRespondedAt(LocalDateTime.now(clock));
  }
}
