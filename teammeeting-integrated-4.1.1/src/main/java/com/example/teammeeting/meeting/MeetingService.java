package com.example.teammeeting.meeting;

import com.example.teammeeting.common.*;
import com.example.teammeeting.user.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {
  private final MeetingRepository meetings;
  private final MeetingMemberRepository members;
  private final MeetingDateRepository dates;
  private final MeetingAccess access;
  private final Clock clock;

  public MeetingService(
      MeetingRepository m,
      MeetingMemberRepository mm,
      MeetingDateRepository d,
      MeetingAccess a,
      Clock c) {
    meetings = m;
    members = mm;
    dates = d;
    access = a;
    clock = c;
  }

  @Transactional
  public Meeting create(String email, MeetingForm form) {
    if (form.getDates() == null || form.getDates().isEmpty() || form.getDates().size() > 7)
      throw new BusinessException("후보 날짜를 1~7개 선택해 주세요.");
    List<LocalDate> parsed = new ArrayList<>();
    try {
      for (String s : form.getDates()) parsed.add(LocalDate.parse(s));
    } catch (Exception e) {
      throw new BusinessException("올바른 후보 날짜를 입력해 주세요.");
    }
    if (new HashSet<>(parsed).size() != parsed.size())
      throw new BusinessException("후보 날짜가 중복되었습니다.");
    if (parsed.stream().anyMatch(d -> d.isBefore(LocalDate.now(clock))))
      throw new BusinessException("과거 날짜는 후보로 등록할 수 없습니다.");
    User user = access.currentUser(email);
    Meeting meeting = new Meeting();
    meeting.setName(form.getName().strip());
    meeting.setCreator(user);
    meeting.setInviteCode(
        UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
    meeting.setStatus(MeetingStatus.OPEN);
    meetings.save(meeting);
    MeetingMember member = new MeetingMember();
    member.setMeeting(meeting);
    member.setUser(user);
    members.save(member);
    for (LocalDate date : parsed) {
      MeetingDate d = new MeetingDate();
      d.setMeeting(meeting);
      d.setDate(date);
      dates.save(d);
    }
    return meeting;
  }

  @Transactional
  public Long join(String email, String code) {
    if (code == null || code.isBlank()) throw new BusinessException("초대 코드를 입력해 주세요.");
    if (code.strip().length() > 16) throw new BusinessException("초대 코드는 16자 이하로 입력해 주세요.");
    Meeting meeting =
        meetings
            .findByInviteCodeForUpdate(code.strip().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new BusinessException("초대 코드를 확인해 주세요."));
    access.requireOpen(meeting);
    User user = access.currentUser(email);
    if (members.findByMeetingIdAndUserId(meeting.getId(), user.getId()).isPresent())
      throw new BusinessException("이미 참여 중인 모임입니다.");
    MeetingMember member = new MeetingMember();
    member.setMeeting(meeting);
    member.setUser(user);
    members.save(member);
    return meeting.getId();
  }
}
