package com.example.teammeeting.coordination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.teammeeting.availability.Availability;
import com.example.teammeeting.availability.AvailabilityRepository;
import com.example.teammeeting.common.BusinessException;
import com.example.teammeeting.meeting.Meeting;
import com.example.teammeeting.meeting.MeetingDate;
import com.example.teammeeting.meeting.MeetingDateRepository;
import com.example.teammeeting.meeting.MeetingMember;
import com.example.teammeeting.meeting.MeetingMemberRepository;
import com.example.teammeeting.meeting.MeetingRepository;
import com.example.teammeeting.meeting.MeetingStatus;
import com.example.teammeeting.user.User;
import com.example.teammeeting.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * B 담당 기능 테스트: 장소 후보, 투표(표 변경), 공통 시간, 팀장 확정.
 *
 * <p>회원·모임 데이터는 이 테스트 안에서만 만든다. 동시 요청(같은 계정의 동시 투표, 반복 확정) 검증은 트랜잭션 없이 스레드를 쓰는 별도
 * 테스트로 추가할 예정이다. (계획서 12~13일 차)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoordinationServiceTest {
  @Autowired CoordinationService service;
  @Autowired UserRepository users;
  @Autowired MeetingRepository meetings;
  @Autowired MeetingMemberRepository members;
  @Autowired MeetingDateRepository dates;
  @Autowired AvailabilityRepository availabilities;
  @Autowired PlaceRepository places;
  @Autowired VoteRepository votes;
  @Autowired PasswordEncoder encoder;

  // ------------------------------------------------------------ 데이터 준비

  User user(String email) {
    User user = new User();
    user.setEmail(email);
    user.setName("이름-" + email.substring(0, email.indexOf('@')));
    user.setPasswordHash(encoder.encode("FixturePassword17!"));
    return users.saveAndFlush(user);
  }

  Meeting meeting(User creator, String inviteCode) {
    Meeting meeting = new Meeting();
    meeting.setName("테스트 모임");
    meeting.setInviteCode(inviteCode);
    meeting.setCreator(creator);
    return meetings.saveAndFlush(meeting);
  }

  MeetingMember join(Meeting meeting, User user, boolean responded) {
    MeetingMember member = new MeetingMember();
    member.setMeeting(meeting);
    member.setUser(user);
    if (responded) {
      member.setRespondedAt(LocalDateTime.now());
    }
    return members.saveAndFlush(member);
  }

  MeetingDate day(Meeting meeting, LocalDate date) {
    MeetingDate d = new MeetingDate();
    d.setMeeting(meeting);
    d.setDate(date);
    return dates.saveAndFlush(d);
  }

  void available(MeetingMember member, MeetingDate date, int hour) {
    Availability a = new Availability();
    a.setMember(member);
    a.setMeetingDate(date);
    a.setStartTime(LocalTime.of(hour, 0));
    availabilities.saveAndFlush(a);
  }

  Place place(Meeting meeting, User by, String name) {
    Place p = new Place();
    p.setMeeting(meeting);
    p.setName(name);
    p.setCreatedBy(by);
    return places.saveAndFlush(p);
  }

  /** 팀장 a, 참여자 b 모두 응답 완료. 내일 15시만 전원 가능(a는 16시도 가능). */
  class Scenario {
    final User a = user("a@scenario.test");
    final User b = user("b@scenario.test");
    final Meeting meeting = meeting(a, "SCENARIO1");
    final LocalDate day = LocalDate.now().plusDays(1);
    final String commonSlot = LocalDateTime.of(day, LocalTime.of(15, 0)).toString();

    Scenario() {
      MeetingMember ma = join(meeting, a, true);
      MeetingMember mb = join(meeting, b, true);
      MeetingDate d = day(meeting, day);
      available(ma, d, 15);
      available(mb, d, 15);
      available(ma, d, 16);
    }
  }

  // ------------------------------------------------------------ 투표

  @Test
  void changingVoteMovesTheSingleVote() {
    User a = user("a@vote.test");
    Meeting m = meeting(a, "VOTE1");
    join(m, a, true);
    Place p1 = place(m, a, "도서관");
    Place p2 = place(m, a, "카페");

    service.castVote(m.getId(), p1.getId(), a.getEmail());
    service.castVote(m.getId(), p2.getId(), a.getEmail());
    service.castVote(m.getId(), p2.getId(), a.getEmail()); // 같은 후보 반복 요청

    assertThat(votes.findByMeetingId(m.getId())).hasSize(1);
    CoordinationView view = service.getCoordination(m.getId(), a.getEmail());
    assertThat(view.getPlaces())
        .extracting(CoordinationView.PlaceView::getVoteCount)
        .containsExactly(0L, 1L); // 이전 후보 1표 감소, 새 후보 1표 증가
    assertThat(view.isHasMyVote()).isTrue();
  }

  @Test
  void placeOfAnotherMeetingIsRejected() {
    User a = user("a@other.test");
    Meeting m1 = meeting(a, "OTHER1");
    Meeting m2 = meeting(a, "OTHER2");
    join(m1, a, true);
    join(m2, a, true);
    Place foreign = place(m2, a, "다른 모임 장소");

    assertThatThrownBy(() -> service.castVote(m1.getId(), foreign.getId(), a.getEmail()))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void nonMemberCannotReadVoteOrAddPlace() {
    User a = user("a@member.test");
    User outsider = user("outsider@member.test");
    Meeting m = meeting(a, "MEMBER1");
    join(m, a, true);
    Place p = place(m, a, "도서관");
    PlaceCreateForm form = new PlaceCreateForm();
    form.setName("새 장소");

    assertThatThrownBy(() -> service.getCoordination(m.getId(), outsider.getEmail()))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.castVote(m.getId(), p.getId(), outsider.getEmail()))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.addPlace(m.getId(), outsider.getEmail(), form))
        .isInstanceOf(BusinessException.class);
  }

  // ------------------------------------------------------------ 공통 시간

  @Test
  void commonSlotsUseEveryMemberAndShowPendingResponses() {
    Scenario s = new Scenario();
    CoordinationView view = service.getCoordination(s.meeting.getId(), s.a.getEmail());
    assertThat(view.getSlots()).hasSize(1); // a만 가능한 16시는 제외
    assertThat(view.getSlots().get(0).getValue()).isEqualTo(s.commonSlot);
    assertThat(view.isPendingResponses()).isFalse();

    // 응답하지 않은 참여자가 들어오면 공통 시간을 계산하지 않고 미응답자를 알려준다.
    User c = user("c@scenario.test");
    join(s.meeting, c, false);
    CoordinationView waiting = service.getCoordination(s.meeting.getId(), s.a.getEmail());
    assertThat(waiting.isPendingResponses()).isTrue();
    assertThat(waiting.getPendingNames()).contains("c");
    assertThat(waiting.getSlots()).isEmpty();
  }

  // ------------------------------------------------------------ 확정

  @Test
  void creatorConfirmsTimeAndPlaceThenEverythingIsBlocked() {
    Scenario s = new Scenario();
    Place p = place(s.meeting, s.a, "도서관");
    service.castVote(s.meeting.getId(), p.getId(), s.b.getEmail());

    service.confirm(s.meeting.getId(), s.a.getEmail(), p.getId(), s.commonSlot);

    Meeting saved = meetings.findById(s.meeting.getId()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(MeetingStatus.CONFIRMED);
    assertThat(saved.getConfirmedDate()).isEqualTo(s.day);
    assertThat(saved.getConfirmedStartTime()).isEqualTo(LocalTime.of(15, 0));
    assertThat(saved.getConfirmedEndTime()).isEqualTo(LocalTime.of(16, 0));
    assertThat(saved.getConfirmedPlace().getId()).isEqualTo(p.getId());
    assertThat(service.getResult(s.meeting.getId(), s.b.getEmail()).isConfirmed()).isTrue();

    // 확정 이후: 투표 변경, 후보 추가, 반복 확정을 서버에서 막는다.
    PlaceCreateForm form = new PlaceCreateForm();
    form.setName("새 장소");
    assertThatThrownBy(() -> service.castVote(s.meeting.getId(), p.getId(), s.a.getEmail()))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.addPlace(s.meeting.getId(), s.a.getEmail(), form))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () -> service.confirm(s.meeting.getId(), s.a.getEmail(), p.getId(), s.commonSlot))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void onlyCreatorCanConfirm() {
    Scenario s = new Scenario();
    Place p = place(s.meeting, s.a, "도서관");
    service.castVote(s.meeting.getId(), p.getId(), s.b.getEmail());

    assertThatThrownBy(
            () -> service.confirm(s.meeting.getId(), s.b.getEmail(), p.getId(), s.commonSlot))
        .isInstanceOf(BusinessException.class);
    assertThat(meetings.findById(s.meeting.getId()).orElseThrow().getStatus())
        .isEqualTo(MeetingStatus.OPEN);
  }

  @Test
  void confirmIsBlockedWhileSomeoneHasNotResponded() {
    Scenario s = new Scenario();
    join(s.meeting, user("c@pending.test"), false);
    Place p = place(s.meeting, s.a, "도서관");
    service.castVote(s.meeting.getId(), p.getId(), s.a.getEmail());

    assertThatThrownBy(
            () -> service.confirm(s.meeting.getId(), s.a.getEmail(), p.getId(), s.commonSlot))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void confirmNeedsACommonSlotAndAtLeastOneVote() {
    Scenario s = new Scenario();
    Place p = place(s.meeting, s.a, "도서관");
    String notCommon = LocalDateTime.of(s.day, LocalTime.of(16, 0)).toString(); // a만 가능

    // 표가 하나도 없는 장소
    assertThatThrownBy(
            () -> service.confirm(s.meeting.getId(), s.a.getEmail(), p.getId(), s.commonSlot))
        .isInstanceOf(BusinessException.class);

    service.castVote(s.meeting.getId(), p.getId(), s.a.getEmail());
    // 전원이 가능하지 않은 시간, 형식이 잘못된 시간
    assertThatThrownBy(
            () -> service.confirm(s.meeting.getId(), s.a.getEmail(), p.getId(), notCommon))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () -> service.confirm(s.meeting.getId(), s.a.getEmail(), p.getId(), "내일 오후"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void onlyTopVotedPlaceCanBeConfirmedAndTiesAreAllowed() {
    Scenario s = new Scenario();
    Place p1 = place(s.meeting, s.a, "도서관");
    Place p2 = place(s.meeting, s.a, "카페");
    service.castVote(s.meeting.getId(), p1.getId(), s.a.getEmail());
    service.castVote(s.meeting.getId(), p1.getId(), s.b.getEmail());

    // p1: 2표, p2: 0표 → p2 확정 불가
    assertThatThrownBy(
            () -> service.confirm(s.meeting.getId(), s.a.getEmail(), p2.getId(), s.commonSlot))
        .isInstanceOf(BusinessException.class);

    // b가 표를 옮기면 1:1 동률 → 팀장이 그중 하나를 선택할 수 있다.
    service.castVote(s.meeting.getId(), p2.getId(), s.b.getEmail());
    CoordinationView view = service.getCoordination(s.meeting.getId(), s.a.getEmail());
    assertThat(view.isTie()).isTrue();
    assertThat(view.getConfirmPlaces()).hasSize(2);
    service.confirm(s.meeting.getId(), s.a.getEmail(), p2.getId(), s.commonSlot);
    assertThat(meetings.findById(s.meeting.getId()).orElseThrow().getConfirmedPlace().getId())
        .isEqualTo(p2.getId());
  }
}
