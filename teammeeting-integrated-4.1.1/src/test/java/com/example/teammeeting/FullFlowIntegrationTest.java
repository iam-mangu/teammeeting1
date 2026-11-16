package com.example.teammeeting;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.teammeeting.availability.*;
import com.example.teammeeting.common.BusinessException;
import com.example.teammeeting.coordination.*;
import com.example.teammeeting.meeting.*;
import com.example.teammeeting.user.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** A의 실제 생성·참여·시간 저장과 B의 투표·확정을 함께 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FullFlowIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AccountService accounts;
  @Autowired MeetingService meetings;
  @Autowired AvailabilityService availability;
  @Autowired CoordinationService coordination;
  @Autowired MeetingDateRepository dates;
  @Autowired MeetingMemberRepository members;
  @Autowired AvailabilityRepository slots;
  @Autowired Clock clock;

  private static final String A = "leader@flow.test";
  private static final String B = "member@flow.test";
  private static final String C = "outsider@flow.test";
  private static final String PASSWORD = "IntegrationPassword17!";

  @BeforeEach
  void reset() {
    jdbc.update("update meetings set confirmed_place_id=null");
    for (String table : List.of("votes", "availabilities", "meeting_dates", "meeting_members",
        "places", "meetings", "app_users")) jdbc.update("delete from " + table);
  }

  private User account(String email) {
    SignupForm form = new SignupForm();
    form.setEmail(email);
    form.setName(email.equals(A) ? "팀장" : email.equals(B) ? "참여자" : "비참여자");
    form.setPassword(PASSWORD);
    return accounts.signup(form);
  }

  private LocalDate tomorrow() { return LocalDate.now(clock).plusDays(1); }

  private record Fixture(Long id, String code, Long dateId, Long first, Long second) {
    String selected(int hour) { return dateId + "_" + hour; }
  }

  private Fixture fixture() {
    account(A); account(B); account(C);
    MeetingForm form = new MeetingForm();
    form.setName("A와 B 통합 모임");
    form.setDates(List.of(tomorrow().toString()));
    Meeting m = meetings.create(A, form);
    meetings.join(B, m.getInviteCode());
    Long date = dates.findAllByMeetingIdOrderByDateAsc(m.getId()).get(0).getId();
    availability.save(m.getId(), A, List.of(date + "_15", date + "_16"), false);
    availability.save(m.getId(), B, List.of(date + "_15"), false);
    PlaceCreateForm place = new PlaceCreateForm();
    place.setName("도서관 스터디룸");
    Long p1 = coordination.addPlace(m.getId(), A, place);
    place.setName("학교 앞 카페");
    Long p2 = coordination.addPlace(m.getId(), B, place);
    coordination.castVote(m.getId(), p1, A);
    return new Fixture(m.getId(), m.getInviteCode(), date, p1, p2);
  }

  private String commonSlot() { return tomorrow() + "T15:00"; }
  private String base(Fixture f) { return "/meetings/" + f.id(); }
  private long count(String table, Long id) {
    return jdbc.queryForObject("select count(*) from " + table + " where meeting_id=?", Long.class, id);
  }
  private String meetingStatus(Fixture f) {
    return jdbc.queryForObject("select status from meetings where id=?", String.class, f.id());
  }

  @Test
  void realLoginSessionsCanCompleteTheEntireHtmlFormFlow() throws Exception {
    for (String email : List.of(A, B, C)) {
      mvc.perform(post("/signup").with(csrf()).param("email", email)
              .param("name", email.equals(A) ? "팀장" : "팀원").param("password", PASSWORD))
          .andExpect(redirectedUrl("/login?registered"));
    }
    MockHttpSession leader = login(A);
    MockHttpSession member = login(B);
    MockHttpSession outsider = login(C);
    var created = mvc.perform(post("/meetings").session(leader).with(csrf())
            .param("name", "함께 완성한 모임").param("dates", tomorrow().toString()))
        .andExpect(status().is3xxRedirection()).andReturn();
    String path = created.getResponse().getRedirectedUrl();
    Long id = Long.valueOf(path.substring(path.lastIndexOf('/') + 1));
    String invite = jdbc.queryForObject("select invite_code from meetings where id=?", String.class, id);
    mvc.perform(post("/meetings/join").session(member).with(csrf()).param("code", invite))
        .andExpect(redirectedUrl(path));
    Long date = dates.findAllByMeetingIdOrderByDateAsc(id).get(0).getId();
    for (MockHttpSession session : List.of(leader, member)) {
      mvc.perform(post(path + "/availability").session(session).with(csrf())
              .param("slots", date + "_15"))
          .andExpect(redirectedUrl(path));
      mvc.perform(get(path).session(session)).andExpect(status().isOk())
          .andExpect(content().string(containsString(path + "/coordination")));
    }
    mvc.perform(get(path + "/coordination").session(leader))
        .andExpect(status().isOk()).andExpect(content().string(containsString("15:00 ~ 16:00")));
    mvc.perform(post(path + "/places").session(member).with(csrf())
            .param("name", "시연 장소").param("memo", "학교 정문 앞"))
        .andExpect(redirectedUrl(path + "/coordination"));
    Long place = jdbc.queryForObject("select id from places where meeting_id=?", Long.class, id);
    mvc.perform(post(path + "/votes").session(member).with(csrf()).param("placeId", place.toString()))
        .andExpect(redirectedUrl(path + "/coordination"));
    mvc.perform(post(path + "/confirm").session(leader).with(csrf())
            .param("placeId", place.toString()).param("slot", commonSlot()))
        .andExpect(redirectedUrl(path + "/result"));
    for (MockHttpSession session : List.of(leader, member)) {
      mvc.perform(get(path + "/result").session(session)).andExpect(status().isOk())
          .andExpect(content().string(containsString("시연 장소")))
          .andExpect(content().string(containsString("15:00 ~ 16:00")));
      mvc.perform(get(path).session(session)).andExpect(status().isOk())
          .andExpect(content().string(containsString(path + "/result")));
    }
    mvc.perform(get(path + "/result").session(outsider)).andExpect(status().isForbidden());
    mvc.perform(post("/logout").session(member).with(csrf())).andExpect(redirectedUrl("/login?logout"));
    mvc.perform(get(path + "/coordination")).andExpect(status().is3xxRedirection());
  }

  private MockHttpSession login(String email) throws Exception {
    return (MockHttpSession) mvc.perform(post("/login").with(csrf())
            .param("email", email).param("password", PASSWORD))
        .andExpect(redirectedUrl("/meetings")).andReturn().getRequest().getSession(false);
  }

  @Test
  void errorsAndEmptyStatesRenderAndPlaceInputIsEscaped() throws Exception {
    Fixture f = fixture(); String path = base(f);
    mvc.perform(get(path + "/result").with(user(A))).andExpect(status().isOk())
        .andExpect(content().string(containsString("아직 확정되지 않은 모임")));
    mvc.perform(post(path + "/places").with(user(A)).with(csrf())
            .param("name", " ").param("memo", "<script>alert(1)</script>"))
        .andExpect(status().isOk()).andExpect(content().string(containsString("장소 이름을 입력")))
        .andExpect(content().string(containsString("&lt;script&gt;")))
        .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    for (String[] input : List.of(new String[]{"x".repeat(81), ""}, new String[]{"장소", "x".repeat(301)})) {
      mvc.perform(post(path + "/places").with(user(A)).with(csrf())
              .param("name", input[0]).param("memo", input[1]))
          .andExpect(status().isOk());
    }
    assertThat(count("places", f.id())).isEqualTo(2);
    mvc.perform(post(path + "/places").with(user(A)).with(csrf())
            .param("name", "<img src=x onerror=alert(1)>"))
        .andExpect(status().is3xxRedirection());
    mvc.perform(get(path + "/coordination").with(user(A))).andExpect(status().isOk())
        .andExpect(content().string(containsString("&lt;img")));
  }

  @Test
  void allBEndpointsEnforceMembershipCsrfAndLeaderRights() throws Exception {
    Fixture f = fixture(); String path = base(f);
    for (String page : List.of("/coordination", "/result"))
      mvc.perform(get(path + page).with(user(C))).andExpect(status().isForbidden());
    for (String action : List.of("/places", "/votes", "/confirm")) {
      mvc.perform(post(path + action).with(user(A)).param("name", "후보")
              .param("placeId", f.first().toString()).param("slot", commonSlot()))
          .andExpect(status().isForbidden());
      mvc.perform(post(path + action).with(user(C)).with(csrf()).param("name", "후보")
              .param("placeId", f.first().toString()).param("slot", commonSlot()))
          .andExpect(status().isForbidden());
    }
    mvc.perform(post(path + "/confirm").with(user(B)).with(csrf())
            .param("placeId", f.first().toString()).param("slot", commonSlot()))
        .andExpect(status().isForbidden());
    assertThat(meetingStatus(f)).isEqualTo("OPEN");
  }

  @Test
  void availabilityChangesRecalculateCommonTimesIncludingNoneAndPending() throws Exception {
    Fixture f = fixture();
    assertThat(coordination.getCoordination(f.id(), A).getSlots()).hasSize(1);
    availability.save(f.id(), B, List.of(), true);
    CoordinationView none = coordination.getCoordination(f.id(), A);
    assertThat(none.isPendingResponses()).isFalse();
    assertThat(none.isNoCommonTime()).isTrue();
    assertThat(none.isCanConfirm()).isFalse();
    assertThatThrownBy(() -> coordination.confirm(f.id(), A, f.first(), commonSlot()))
        .isInstanceOf(BusinessException.class);
    availability.save(f.id(), B, List.of(f.selected(16)), false);
    assertThat(coordination.getCoordination(f.id(), A).getSlots()).extracting(CoordinationView.SlotView::getValue)
        .containsExactly(tomorrow() + "T16:00");
    meetings.join(C, f.code());
    CoordinationView pending = coordination.getCoordination(f.id(), A);
    assertThat(pending.isPendingResponses()).isTrue();
    assertThat(pending.isNoCommonTime()).isFalse();
    assertThat(pending.getSlots()).isEmpty();
    mvc.perform(get(base(f) + "/coordination").with(user(A))).andExpect(status().isOk())
        .andExpect(content().string(containsString("아직 응답하지 않은 참여자")));
  }

  @Test
  void commonSlotsAreSortedAcrossDatesAndUseOnlyTheIntersection() {
    account(A); account(B);
    MeetingForm form = new MeetingForm(); form.setName("날짜 정렬");
    form.setDates(List.of(tomorrow().plusDays(1).toString(), tomorrow().toString()));
    Meeting m = meetings.create(A, form); meetings.join(B, m.getInviteCode());
    List<MeetingDate> ds = dates.findAllByMeetingIdOrderByDateAsc(m.getId());
    String d1 = ds.get(0).getId() + "_"; String d2 = ds.get(1).getId() + "_";
    availability.save(m.getId(), A, List.of(d2 + "20", d1 + "9", d1 + "15", d1 + "16"), false);
    availability.save(m.getId(), B, List.of(d1 + "15", d2 + "20", d1 + "9"), false);
    assertThat(coordination.getCoordination(m.getId(), A).getSlots())
        .extracting(CoordinationView.SlotView::getValue)
        .containsExactly(tomorrow() + "T09:00", tomorrow() + "T15:00", tomorrow().plusDays(1) + "T20:00");
  }

  @Test
  void confirmedMeetingBlocksEveryMutationAcrossAAndB() throws Exception {
    Fixture f = fixture(); coordination.confirm(f.id(), A, f.first(), commonSlot());
    String path = base(f);
    mvc.perform(post("/meetings/join").with(user(C)).with(csrf()).param("code", f.code()))
        .andExpect(redirectedUrl("/meetings"))
        .andExpect(flash().attribute("error", "이미 확정된 모임은 변경할 수 없습니다."));
    for (String action : List.of("/availability", "/availability/none", "/places", "/votes", "/confirm")) {
      mvc.perform(post(path + action).with(user(A)).with(csrf()).param("slots", f.selected(16))
              .param("name", "새 장소").param("placeId", f.second().toString()).param("slot", commonSlot()))
          .andExpect(status().isBadRequest());
    }
    assertThat(count("meeting_members", f.id())).isEqualTo(2);
    assertThat(count("places", f.id())).isEqualTo(2);
    assertThat(count("votes", f.id())).isEqualTo(1);
    assertThat(slots.findAllByMemberMeetingId(f.id())).hasSize(3);
  }

  @Test
  void concurrentVotesKeepExactlyOneVotePerMember() throws Exception {
    Fixture f = fixture(); AtomicInteger next = new AtomicInteger();
    race(Collections.nCopies(6, () -> coordination.castVote(f.id(),
        next.getAndIncrement() % 2 == 0 ? f.first() : f.second(), B)));
    assertThat(count("votes", f.id())).isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from votes where meeting_id=? and user_id=(select id from app_users where email=?)",
        Long.class, f.id(), B)).isEqualTo(1);
  }

  @Test
  void concurrentConfirmRequestsSucceedExactlyOnce() throws Exception {
    Fixture f = fixture(); AtomicInteger success = new AtomicInteger(); AtomicInteger rejected = new AtomicInteger();
    race(Collections.nCopies(6, () -> attempt(() -> coordination.confirm(f.id(), A, f.first(), commonSlot()), success, rejected)));
    assertThat(success.get()).isEqualTo(1); assertThat(rejected.get()).isEqualTo(5);
    assertThat(coordination.getResult(f.id(), B).getPlaceName()).isEqualTo("도서관 스터디룸");
    assertThat(meetingStatus(f)).isEqualTo("CONFIRMED");
  }

  @Test
  void joinAndConfirmCannotBothSucceed() throws Exception {
    Fixture f = fixture(); AtomicInteger success = new AtomicInteger(); AtomicInteger rejected = new AtomicInteger();
    race(List.of(() -> attempt(() -> meetings.join(C, f.code()), success, rejected),
        () -> attempt(() -> coordination.confirm(f.id(), A, f.first(), commonSlot()), success, rejected)));
    assertThat(success.get()).isEqualTo(1); assertThat(rejected.get()).isEqualTo(1);
    assertThat(count("meeting_members", f.id())).isEqualTo(meetingStatus(f).equals("CONFIRMED") ? 2 : 3);
  }

  @Test
  void timeReplacementAndConfirmCannotLeaveAnInvalidConfirmedSlot() throws Exception {
    Fixture f = fixture(); AtomicInteger success = new AtomicInteger(); AtomicInteger rejected = new AtomicInteger();
    race(List.of(() -> attempt(() -> availability.save(f.id(), B, List.of(), true), success, rejected),
        () -> attempt(() -> coordination.confirm(f.id(), A, f.first(), commonSlot()), success, rejected)));
    assertThat(success.get()).isEqualTo(1); assertThat(rejected.get()).isEqualTo(1);
    assertThat(coordination.getCoordination(f.id(), A).getSlots()).hasSize(meetingStatus(f).equals("CONFIRMED") ? 1 : 0);
  }

  @Test
  void voteChangeAndConfirmCannotConfirmALosingPlace() throws Exception {
    Fixture f = fixture(); coordination.castVote(f.id(), f.second(), B);
    AtomicInteger success = new AtomicInteger(); AtomicInteger rejected = new AtomicInteger();
    race(List.of(() -> attempt(() -> coordination.castVote(f.id(), f.first(), B), success, rejected),
        () -> attempt(() -> coordination.confirm(f.id(), A, f.second(), commonSlot()), success, rejected)));
    assertThat(success.get()).isEqualTo(1); assertThat(rejected.get()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from votes where meeting_id=? and place_id=?", Long.class,
        f.id(), f.second())).isEqualTo(meetingStatus(f).equals("CONFIRMED") ? 1 : 0);
  }

  @Test
  void placeAdditionIsSerializedWithConfirmation() throws Exception {
    Fixture f = fixture(); AtomicInteger added = new AtomicInteger(); AtomicInteger confirmed = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger(); PlaceCreateForm form = new PlaceCreateForm(); form.setName("마지막 후보");
    race(List.of(() -> attempt(() -> coordination.addPlace(f.id(), B, form), added, rejected),
        () -> attempt(() -> coordination.confirm(f.id(), A, f.first(), commonSlot()), confirmed, rejected)));
    assertThat(confirmed.get()).isEqualTo(1); assertThat(meetingStatus(f)).isEqualTo("CONFIRMED");
    assertThat(added.get() + rejected.get()).isEqualTo(1);
    assertThat(count("places", f.id())).isEqualTo(2 + added.get());
    assertThatThrownBy(() -> coordination.addPlace(f.id(), B, form)).isInstanceOf(BusinessException.class);
  }

  private void attempt(Runnable action, AtomicInteger success, AtomicInteger rejected) {
    try { action.run(); success.incrementAndGet(); }
    catch (BusinessException e) { rejected.incrementAndGet(); }
  }

  private void race(List<Runnable> actions) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(actions.size());
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (Runnable action : actions) futures.add(pool.submit(() -> {
        try { start.await(); action.run(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
      }));
      start.countDown();
      for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
    } finally { pool.shutdownNow(); }
  }
}
