package com.example.teammeeting;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.teammeeting.availability.*;
import com.example.teammeeting.common.*;
import com.example.teammeeting.meeting.*;
import com.example.teammeeting.user.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AIntegrationTest {
  @Autowired AccountService accounts;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired MeetingService meetings;
  @Autowired MeetingMemberRepository members;
  @Autowired MeetingDateRepository dates;
  @Autowired AvailabilityService availability;
  @Autowired AvailabilityRepository availabilityRepository;
  @Autowired PageDataService pages;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;

  @BeforeEach
  void reset() {
    jdbc.update("update meetings set confirmed_place_id=null");
    for (String table :
        List.of(
            "votes",
            "availabilities",
            "meeting_dates",
            "meeting_members",
            "places",
            "meetings",
            "app_users")) jdbc.update("delete from " + table);
  }

  User account(String email) {
    SignupForm f = new SignupForm();
    f.setEmail(email);
    f.setName("테스트팀원");
    f.setPassword("LocalPassword17!");
    return accounts.signup(f);
  }

  Meeting meeting(String email) {
    MeetingForm f = new MeetingForm();
    f.setName("팀 프로젝트");
    f.setDates(List.of(LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).toString()));
    return meetings.create(email, f);
  }

  String slot(Long id, int hour) {
    return dates.findAllByMeetingIdOrderByDateAsc(id).get(0).getId() + "_" + hour;
  }

  @Test
  void accountHashesPasswordAndRejectsDuplicates() {
    User user = account("A@test.com");
    assertThat(user.getEmail()).isEqualTo("a@test.com");
    assertThat(user.getPasswordHash()).isNotEqualTo("LocalPassword17!");
    assertThat(encoder.matches("LocalPassword17!", user.getPasswordHash())).isTrue();
    assertThatThrownBy(() -> account("a@test.com")).isInstanceOf(BusinessException.class);
  }

  @Test
  void loginAndCsrfWork() throws Exception {
    account("a@test.com");
    mvc.perform(
            post("/login")
                .with(csrf())
                .param("email", "A@test.com")
                .param("password", "LocalPassword17!"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/meetings"));
    mvc.perform(get("/meetings")).andExpect(status().is3xxRedirection());
    mvc.perform(post("/meetings").with(user("a@test.com"))).andExpect(status().isForbidden());
    mvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
  }

  @Test
  void creatorJoinsAutomaticallyAndCodeJoinIsUnique() {
    account("a@test.com");
    account("b@test.com");
    Meeting m = meeting("a@test.com");
    assertThat(members.findAllByMeetingIdOrderByIdAsc(m.getId())).hasSize(1);
    meetings.join("b@test.com", m.getInviteCode().toLowerCase(Locale.ROOT));
    assertThat(members.findAllByMeetingIdOrderByIdAsc(m.getId())).hasSize(2);
    assertThatThrownBy(() -> meetings.join("b@test.com", m.getInviteCode()))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> meetings.join("b@test.com", "INVALIDCODE"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void replacementAndNoneResponsesPersist() {
    User a = account("a@test.com");
    account("b@test.com");
    Meeting m = meeting("a@test.com");
    meetings.join("b@test.com", m.getInviteCode());
    Long memberId = members.findByMeetingIdAndUserId(m.getId(), a.getId()).orElseThrow().getId();
    assertThat(pages.detail(m.getId(), "b@test.com").get("myResponded")).isEqualTo(false);
    availability.save(
        m.getId(), "a@test.com", List.of(slot(m.getId(), 9), slot(m.getId(), 14)), false);
    availability.save(
        m.getId(), "a@test.com", List.of(slot(m.getId(), 14), slot(m.getId(), 14)), false);
    assertThat(availabilityRepository.findAllByMemberId(memberId)).hasSize(1);
    assertThat(pages.detail(m.getId(), "a@test.com").get("myResponded")).isEqualTo(true);
    availability.save(m.getId(), "b@test.com", List.of(), true);
    assertThat(pages.detail(m.getId(), "b@test.com").get("myNone")).isEqualTo(true);
  }

  @Test
  void invalidSlotsDoNotEraseSelections() {
    User a = account("a@test.com");
    Meeting m = meeting("a@test.com");
    availability.save(m.getId(), "a@test.com", List.of(slot(m.getId(), 14)), false);
    assertThatThrownBy(() -> availability.save(m.getId(), "a@test.com", List.of("broken"), false))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () -> availability.save(m.getId(), "a@test.com", List.of(slot(m.getId(), 21)), false))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> availability.save(m.getId(), "a@test.com", List.of(), false))
        .isInstanceOf(BusinessException.class);
    Long memberId = members.findByMeetingIdAndUserId(m.getId(), a.getId()).orElseThrow().getId();
    assertThat(availabilityRepository.findAllByMemberId(memberId)).hasSize(1);
  }

  @Test
  void foreignDateAndUnauthorizedMemberAreRejected() throws Exception {
    account("a@test.com");
    account("outsider@test.com");
    Meeting first = meeting("a@test.com");
    Meeting second = meeting("a@test.com");
    assertThatThrownBy(
            () ->
                availability.save(
                    first.getId(), "a@test.com", List.of(slot(second.getId(), 14)), false))
        .isInstanceOf(BusinessException.class);
    mvc.perform(get("/meetings/" + first.getId()).with(user("outsider@test.com")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/meetings/" + first.getId() + "/availability/none")
                .with(user("outsider@test.com"))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void confirmedStateBlocksAChangesWithoutImplementingB() {
    account("a@test.com");
    account("b@test.com");
    Meeting m = meeting("a@test.com");
    // B의 확정 로직을 구현하지 않고, 확정 상태를 fixture로만 만들어 A의 보호 규칙을 검증한다.
    jdbc.update("update meetings set status='CONFIRMED' where id=?", m.getId());
    assertThatThrownBy(() -> availability.save(m.getId(), "a@test.com", List.of(), true))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> meetings.join("b@test.com", m.getInviteCode()))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void formsAndAScreensRenderAndRejectInvalidDates() throws Exception {
    account("a@test.com");
    Meeting m = meeting("a@test.com");
    mvc.perform(get("/meetings").with(user("a@test.com"))).andExpect(status().isOk());
    mvc.perform(get("/meetings/new").with(user("a@test.com"))).andExpect(status().isOk());
    mvc.perform(get("/meetings/" + m.getId()).with(user("a@test.com")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/meetings/" + m.getId() + "/coordination")));
    mvc.perform(
            post("/signup")
                .with(csrf())
                .param("email", "bad")
                .param("name", " ")
                .param("password", "short"))
        .andExpect(status().isOk());
    MeetingForm f = new MeetingForm();
    f.setName("회의");
    String day = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).toString();
    f.setDates(List.of(day, day));
    assertThatThrownBy(() -> meetings.create("a@test.com", f))
        .isInstanceOf(BusinessException.class);
    f.setDates(List.of(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).toString()));
    assertThatThrownBy(() -> meetings.create("a@test.com", f))
        .isInstanceOf(BusinessException.class);
  }
}
