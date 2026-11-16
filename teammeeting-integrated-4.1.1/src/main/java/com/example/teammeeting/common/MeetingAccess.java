package com.example.teammeeting.common;

import com.example.teammeeting.meeting.*;
import com.example.teammeeting.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MeetingAccess {
  private final MeetingRepository meetings;
  private final MeetingMemberRepository members;
  private final UserRepository users;

  public MeetingAccess(
      MeetingRepository meetings, MeetingMemberRepository members, UserRepository users) {
    this.meetings = meetings;
    this.members = members;
    this.users = users;
  }

  public User currentUser(String email) {
    return users
        .findByEmail(email)
        .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."));
  }

  public Meeting requireMember(Long id, String email, boolean write) {
    Meeting meeting =
        (write ? meetings.findByIdForUpdate(id) : meetings.findById(id))
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."));
    User user = currentUser(email);
    if (members.findByMeetingIdAndUserId(id, user.getId()).isEmpty())
      throw new BusinessException(HttpStatus.FORBIDDEN, "이 모임의 참여자만 이용할 수 있습니다.");
    if (write) requireOpen(meeting);
    return meeting;
  }

  public void requireOpen(Meeting m) {
    if (m.getStatus() != MeetingStatus.OPEN) throw new BusinessException("이미 확정된 모임은 변경할 수 없습니다.");
  }
}
