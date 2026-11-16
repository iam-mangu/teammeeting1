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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AMergeReadinessTest {
  @Autowired AccountService accounts;
  @Autowired MeetingService meetings;
  @Autowired MeetingMemberRepository members;
  @Autowired MeetingDateRepository dates;
  @Autowired AvailabilityService availability;
  @Autowired AvailabilityRepository slots;
  @Autowired PageDataService pages;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired Clock clock;

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
    SignupForm form = new SignupForm();
    form.setEmail(email);
    form.setName("검증 회원");
    form.setPassword("TestPassword17!");
    return accounts.signup(form);
  }

  Meeting meeting(String email, int days) {
    MeetingForm form = new MeetingForm();
    form.setName("A 담당 점검");
    form.setDates(
        IntStream.rangeClosed(1, days)
            .mapToObj(i -> LocalDate.now(clock).plusDays(i).toString())
            .toList());
    return meetings.create(email, form);
  }

  @Test
  void allSevenDaysAndTwelveHoursCanBeSaved() {
    User a = account("a@example.test");
    Meeting m = meeting(a.getEmail(), 7);
    List<String> selected = new ArrayList<>();
    for (MeetingDate date : dates.findAllByMeetingIdOrderByDateAsc(m.getId()))
      for (int h = 9; h <= 20; h++) selected.add(date.getId() + "_" + h);
    availability.save(m.getId(), a.getEmail(), selected, false);
    assertThat(slots.findAllByMemberMeetingId(m.getId())).hasSize(84);
    MeetingForm tooMany = new MeetingForm();
    tooMany.setName("날짜 초과");
    tooMany.setDates(
        IntStream.rangeClosed(1, 8)
            .mapToObj(i -> LocalDate.now(clock).plusDays(i).toString())
            .toList());
    assertThatThrownBy(() -> meetings.create(a.getEmail(), tooMany))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void signupHttpPreservesSafeFieldsWithoutEchoingPassword() throws Exception {
    mvc.perform(get("/signup")).andExpect(status().isOk());
    String password = "NeverEchoPassword17!";
    mvc.perform(
            post("/signup")
                .with(csrf())
                .param("email", "bad-email")
                .param("name", "테스트 이름")
                .param("password", password))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("bad-email")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("테스트 이름")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(password))));
    mvc.perform(
            post("/signup")
                .with(csrf())
                .param("email", "NEW@example.test")
                .param("name", "새 회원")
                .param("password", password))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));
  }

  @Test
  void meetingHttpEscapesNameAndDoesNotExposeOtherMembersMeetings() throws Exception {
    account("a@example.test");
    account("b@example.test");
    mvc.perform(
            post("/meetings")
                .with(user("a@example.test"))
                .with(csrf())
                .param("name", "<script>alert(1)</script>")
                .param("dates", LocalDate.now(clock).plusDays(1).toString()))
        .andExpect(status().is3xxRedirection());
    Long id = jdbc.queryForObject("select id from meetings", Long.class);
    mvc.perform(get("/meetings/" + id).with(user("a@example.test")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
    assertThat((List<?>) pages.list("b@example.test").get("meetings")).isEmpty();
  }

  @Test
  void memberStatusesDistinguishMissingAvailableAndNone() {
    User a = account("a@example.test");
    account("b@example.test");
    account("c@example.test");
    Meeting m = meeting(a.getEmail(), 1);
    meetings.join("b@example.test", m.getInviteCode());
    meetings.join("c@example.test", m.getInviteCode());
    availability.save(
        m.getId(),
        a.getEmail(),
        List.of(dates.findAllByMeetingIdOrderByDateAsc(m.getId()).get(0).getId() + "_14"),
        false);
    availability.save(m.getId(), "b@example.test", List.of(), true);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> people =
        (List<Map<String, Object>>) pages.detail(m.getId(), a.getEmail()).get("people");
    assertThat(people.stream().filter(p -> Boolean.TRUE.equals(p.get("noAvailability"))).count())
        .isEqualTo(1);
    assertThat(people.stream().filter(p -> Boolean.TRUE.equals(p.get("hasAvailability"))).count())
        .isEqualTo(1);
    assertThat(people.stream().filter(p -> Boolean.FALSE.equals(p.get("responded"))).count())
        .isEqualTo(1);
  }

  @Test
  void concurrentInvitationJoinKeepsOneMembership() throws Exception {
    account("a@example.test");
    account("b@example.test");
    Meeting m = meeting("a@example.test", 1);
    AtomicInteger success = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    runConcurrent(
        6,
        () -> {
          try {
            meetings.join("b@example.test", m.getInviteCode());
            success.incrementAndGet();
          } catch (BusinessException e) {
            rejected.incrementAndGet();
          }
        });
    assertThat(success.get()).isEqualTo(1);
    assertThat(rejected.get()).isEqualTo(5);
    assertThat(members.findAllByMeetingIdOrderByIdAsc(m.getId())).hasSize(2);
  }

  @Test
  void concurrentTimeReplacementLeavesOneCompleteSelection() throws Exception {
    User a = account("a@example.test");
    Meeting m = meeting(a.getEmail(), 1);
    Long dateId = dates.findAllByMeetingIdOrderByDateAsc(m.getId()).get(0).getId();
    AtomicInteger nextHour = new AtomicInteger(9);
    runConcurrent(
        6,
        () ->
            availability.save(
                m.getId(),
                a.getEmail(),
                List.of(dateId + "_" + nextHour.getAndIncrement()),
                false));
    assertThat(slots.findAllByMemberMeetingId(m.getId())).hasSize(1);
    assertThat(
            members.findByMeetingIdAndUserId(m.getId(), a.getId()).orElseThrow().getRespondedAt())
        .isNotNull();
  }

  private void runConcurrent(int count, Runnable action) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(count);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < count; i++)
        futures.add(
            pool.submit(
                () -> {
                  try {
                    start.await();
                    action.run();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                  }
                }));
      start.countDown();
      for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
  }
}
