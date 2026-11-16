package com.example.teammeeting.common;

import com.example.teammeeting.availability.AvailabilityRepository;
import com.example.teammeeting.meeting.*;
import com.example.teammeeting.user.User;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A 화면에 필요한 조회만 담당한다. B 화면은 별도 조회 서비스를 추가해 연결한다. */
@Service
@Transactional(readOnly = true)
public class PageDataService {
  private final MeetingAccess access;
  private final MeetingMemberRepository members;
  private final MeetingDateRepository dates;
  private final AvailabilityRepository availability;

  public PageDataService(
      MeetingAccess access,
      MeetingMemberRepository members,
      MeetingDateRepository dates,
      AvailabilityRepository availability) {
    this.access = access;
    this.members = members;
    this.dates = dates;
    this.availability = availability;
  }

  public static Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
    return result;
  }

  public Map<String, Object> list(String email) {
    User user = access.currentUser(email);
    List<Map<String, Object>> list = new ArrayList<>();
    for (MeetingMember member : members.findAllByUserIdOrderByIdDesc(user.getId())) {
      Meeting m = member.getMeeting();
      boolean open = m.getStatus() == MeetingStatus.OPEN;
      list.add(
          map(
              "id",
              m.getId(),
              "name",
              m.getName(),
              "status",
              open ? "조율 중" : "확정 완료",
              "open",
              open,
              "leader",
              m.getCreator().getName()));
    }
    return map(
        "displayName",
        user.getName(),
        "meetings",
        list,
        "hasMeetings",
        !list.isEmpty(),
        "meetingCount",
        list.size());
  }

  public Map<String, Object> detail(Long id, String email) {
    Meeting m = access.requireMember(id, email, false);
    User user = access.currentUser(email);
    List<MeetingMember> all = members.findAllByMeetingIdOrderByIdAsc(id);
    MeetingMember mine =
        all.stream()
            .filter(x -> x.getUser().getId().equals(user.getId()))
            .findFirst()
            .orElseThrow();
    Set<String> selected =
        availability.findAllByMemberId(mine.getId()).stream()
            .map(a -> a.getMeetingDate().getId() + "_" + a.getStartTime().getHour())
            .collect(Collectors.toSet());
    Set<Long> availableMembers =
        availability.findAllByMemberMeetingId(id).stream()
            .map(a -> a.getMember().getId())
            .collect(Collectors.toSet());
    List<Map<String, Object>> people =
        all.stream()
            .map(
                x ->
                    map(
                        "name",
                        x.getUser().getName(),
                        "leader",
                        x.getUser().getId().equals(m.getCreator().getId()),
                        "responded",
                        x.getRespondedAt() != null,
                        "noAvailability",
                        x.getRespondedAt() != null && !availableMembers.contains(x.getId()),
                        "hasAvailability",
                        x.getRespondedAt() != null && availableMembers.contains(x.getId())))
            .toList();
    List<Map<String, Object>> days = new ArrayList<>();
    for (MeetingDate date : dates.findAllByMeetingIdOrderByDateAsc(id)) {
      List<Map<String, Object>> slots = new ArrayList<>();
      for (int hour = 9; hour <= 20; hour++) {
        String key = date.getId() + "_" + hour;
        slots.add(
            map(
                "key",
                key,
                "label",
                String.format("%02d:00", hour),
                "end",
                String.format("%02d:00", hour + 1),
                "selected",
                selected.contains(key)));
      }
      days.add(
          map(
              "label",
              date.getDate().format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)),
              "slots",
              slots));
    }
    return map(
        "meetingId",
        id,
        "meetingName",
        m.getName(),
        "inviteCode",
        m.getInviteCode(),
        "open",
        m.getStatus() == MeetingStatus.OPEN,
        "confirmed",
        m.getStatus() == MeetingStatus.CONFIRMED,
        "people",
        people,
        "memberCount",
        all.size(),
        "respondedCount",
        all.stream().filter(x -> x.getRespondedAt() != null).count(),
        "days",
        days,
        "myResponded",
        mine.getRespondedAt() != null,
        "myNone",
        mine.getRespondedAt() != null && selected.isEmpty());
  }
}
