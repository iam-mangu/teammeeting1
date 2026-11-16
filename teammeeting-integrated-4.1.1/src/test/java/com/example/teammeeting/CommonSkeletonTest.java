package com.example.teammeeting;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.teammeeting.availability.*;
import com.example.teammeeting.common.*;
import com.example.teammeeting.coordination.*;
import com.example.teammeeting.meeting.*;
import com.example.teammeeting.user.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 공통 구조 검증 및 B 테스트 데이터 작성 예시. 업무 기능 구현은 포함하지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommonSkeletonTest {
  @Autowired UserRepository users;
  @Autowired MeetingRepository meetings;
  @Autowired MeetingMemberRepository members;
  @Autowired MeetingDateRepository dates;
  @Autowired AvailabilityRepository availability;
  @Autowired MeetingAccess access;
  @Autowired EntityManager entityManager;
  @Autowired PasswordEncoder encoder;
  @Autowired Clock clock;
  @Autowired MockMvc mvc;

  // 테스트에서만 직접 만든다. 실제 회원가입과 모임 생성은 A가 구현한다.
  User fixtureUser(String email) {
    User user = new User();
    user.setEmail(email);
    user.setName("테스트 회원");
    user.setPasswordHash(encoder.encode("FixturePassword17!"));
    return users.saveAndFlush(user);
  }

  Meeting fixtureMeeting(User creator) {
    Meeting meeting = new Meeting();
    meeting.setName("테스트 모임");
    meeting.setInviteCode("BASE411TEST");
    meeting.setCreator(creator);
    meetings.saveAndFlush(meeting);
    MeetingMember member = new MeetingMember();
    member.setMeeting(meeting);
    member.setUser(creator);
    members.saveAndFlush(member);
    return meeting;
  }

  @Test
  void pinnedVersionAndLombokWork() throws Exception {
    assertThat(SpringBootVersion.getVersion()).isEqualTo("4.1.1");
    assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    User user = fixtureUser("lombok@example.test");
    assertThat(user.getId()).isNotNull();
    assertThat(user.getEmail()).isEqualTo("lombok@example.test");
    assertThatThrownBy(() -> User.class.getMethod("setId", Long.class))
        .isInstanceOf(NoSuchMethodException.class);
  }

  @Test
  void allSevenEntitiesAndSharedQueriesAreUsable() {
    User user = fixtureUser("model@example.test");
    Meeting meeting = fixtureMeeting(user);
    assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.OPEN);
    MeetingMember member =
        members.findByMeetingIdAndUserId(meeting.getId(), user.getId()).orElseThrow();
    assertThat(member.getRespondedAt()).isNull();
    MeetingDate date = new MeetingDate();
    date.setMeeting(meeting);
    date.setDate(LocalDate.now(clock).plusDays(1));
    dates.saveAndFlush(date);
    Availability slot = new Availability();
    slot.setMember(member);
    slot.setMeetingDate(date);
    slot.setStartTime(LocalTime.of(14, 0));
    availability.saveAndFlush(slot);
    Place place = new Place();
    place.setMeeting(meeting);
    place.setName("테스트 장소");
    place.setCreatedBy(user);
    entityManager.persist(place);
    Vote vote = new Vote();
    vote.setMeeting(meeting);
    vote.setUser(user);
    vote.setPlace(place);
    entityManager.persist(vote);
    entityManager.flush();
    assertThat(place.getId()).isNotNull();
    assertThat(vote.getId()).isNotNull();
    assertThat(availability.findAllByMemberMeetingId(meeting.getId())).hasSize(1);
    assertThat(dates.findAllByMeetingIdOrderByDateAsc(meeting.getId())).hasSize(1);
    assertThat(members.findAllByMeetingIdOrderByIdAsc(meeting.getId())).hasSize(1);
  }

  @Test
  void duplicateEmailIsBlockedByDatabase() {
    fixtureUser("duplicate@example.test");
    assertThatThrownBy(() -> fixtureUser("duplicate@example.test"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void oneVotePerMeetingAndUserIsASchemaConstraint() {
    User user = fixtureUser("vote-schema@example.test");
    Meeting meeting = fixtureMeeting(user);
    Place place = new Place();
    place.setMeeting(meeting);
    place.setName("장소");
    place.setCreatedBy(user);
    entityManager.persist(place);
    Vote first = new Vote();
    first.setMeeting(meeting);
    first.setUser(user);
    first.setPlace(place);
    entityManager.persist(first);
    entityManager.flush();
    Vote duplicate = new Vote();
    duplicate.setMeeting(meeting);
    duplicate.setUser(user);
    duplicate.setPlace(place);
    assertThatThrownBy(
            () -> {
              entityManager.persist(duplicate);
              entityManager.flush();
            })
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void commonAccessAndLocksAreAvailable() {
    User creator = fixtureUser("member@example.test");
    fixtureUser("outsider@example.test");
    Meeting meeting = fixtureMeeting(creator);
    assertThat(access.currentUser(creator.getEmail()).getId()).isEqualTo(creator.getId());
    assertThat(access.requireMember(meeting.getId(), creator.getEmail(), true).getId())
        .isEqualTo(meeting.getId());
    assertThat(meetings.findByInviteCodeForUpdate(meeting.getInviteCode())).isPresent();
    assertThatThrownBy(() -> access.requireMember(meeting.getId(), "outsider@example.test", false))
        .isInstanceOf(BusinessException.class);
    meeting.setStatus(MeetingStatus.CONFIRMED);
    assertThatThrownBy(() -> access.requireMember(meeting.getId(), creator.getEmail(), true))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void mustacheBootstrapAndCsrfAreConnected() throws Exception {
    mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    mvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    mvc.perform(get("/vendor/bootstrap.min.css")).andExpect(status().isOk());
    mvc.perform(get("/meetings/123/coordination")).andExpect(status().is3xxRedirection());
    mvc.perform(post("/login").param("email", "nobody@example.test").param("password", "bad"))
        .andExpect(status().isForbidden());
  }

  @Test
  void loginUsesPersistedMemberWithoutShippingDefaultAccounts() throws Exception {
    fixtureUser("login@example.test");
    mvc.perform(
            post("/login")
                .with(csrf())
                .param("email", "LOGIN@example.test")
                .param("password", "FixturePassword17!"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/meetings"));
  }
}
