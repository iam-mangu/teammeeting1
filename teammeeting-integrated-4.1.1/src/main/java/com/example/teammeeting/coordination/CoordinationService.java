package com.example.teammeeting.coordination;

import com.example.teammeeting.availability.Availability;
import com.example.teammeeting.availability.AvailabilityRepository;
import com.example.teammeeting.common.BusinessException;
import com.example.teammeeting.common.MeetingAccess;
import com.example.teammeeting.meeting.Meeting;
import com.example.teammeeting.meeting.MeetingDate;
import com.example.teammeeting.meeting.MeetingDateRepository;
import com.example.teammeeting.meeting.MeetingMember;
import com.example.teammeeting.meeting.MeetingMemberRepository;
import com.example.teammeeting.meeting.MeetingStatus;
import com.example.teammeeting.user.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B 담당: 장소 후보, 투표, 공통 시간 계산, 팀장 확정.
 *
 * <p>쓰기 메서드는 모두 MeetingAccess.requireMember(id, email, true)로 시작한다. 이 호출이 모임 행을 잠그고(동시 요청을 한 줄로
 * 세운다), 참여자와 OPEN 상태를 함께 검사한다. (docs/B_개발_시작.md 의 공통 규칙)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoordinationService {

  /** 시간 버튼은 09시~20시 시작, 길이 1시간 (마지막 버튼은 20:00~21:00). */
  private static final int FIRST_HOUR = 9;

  private static final int LAST_HOUR = 20;

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);
  private static final DateTimeFormatter FULL_DAY =
      DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN);
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final MeetingAccess access;
  private final PlaceRepository placeRepository;
  private final VoteRepository voteRepository;
  private final MeetingMemberRepository memberRepository;
  private final MeetingDateRepository dateRepository;
  private final AvailabilityRepository availabilityRepository;

  // ---------------------------------------------------------------- 조회

  /** 장소·투표 화면. 모임 참여자만 볼 수 있다. */
  public CoordinationView getCoordination(Long meetingId, String email) {
    Meeting meeting = access.requireMember(meetingId, email, false);
    User me = access.currentUser(email);

    List<MeetingMember> members = memberRepository.findAllByMeetingIdOrderByIdAsc(meetingId);
    List<String> waiting = waitingNames(members);
    // 전체 참여자 기준으로 계산한다. 미응답자가 있으면 공통 시간은 없는 것으로 본다.
    List<LocalDateTime> common = waiting.isEmpty() ? findCommonSlots(meetingId, members) : List.of();

    Map<Long, Long> counts = voteCounts(meetingId);
    long maxVotes = counts.values().stream().mapToLong(Long::longValue).max().orElse(0L);
    Long myPlaceId =
        voteRepository
            .findByMeetingIdAndUserId(meetingId, me.getId())
            .map(v -> v.getPlace().getId())
            .orElse(null);

    List<CoordinationView.PlaceView> placeViews = new ArrayList<>();
    for (Place p : placeRepository.findByMeetingIdOrderByIdAsc(meetingId)) {
      long count = counts.getOrDefault(p.getId(), 0L);
      String memo = p.getMemo() == null ? "" : p.getMemo();
      placeViews.add(
          CoordinationView.PlaceView.builder()
              .id(p.getId())
              .name(p.getName())
              .memo(memo)
              .hasMemo(!memo.isBlank())
              .createdByName(p.getCreatedBy().getName())
              .voteCount(count)
              .mine(p.getId().equals(myPlaceId))
              .top(maxVotes >= 1 && count == maxVotes)
              .build());
    }
    List<CoordinationView.PlaceView> topPlaces =
        placeViews.stream().filter(CoordinationView.PlaceView::isTop).toList();

    boolean confirmed = meeting.getStatus() == MeetingStatus.CONFIRMED;
    boolean creator = meeting.getCreator().getId().equals(me.getId());
    boolean canConfirm =
        creator && !confirmed && waiting.isEmpty() && !common.isEmpty() && !topPlaces.isEmpty();

    String blocked = "";
    if (creator && !confirmed && !canConfirm) {
      if (!waiting.isEmpty()) {
        blocked = "전원이 가능한 시간을 응답해야 확정할 수 있습니다.";
      } else if (common.isEmpty()) {
        blocked = "전원이 가능한 시간이 없어 확정할 수 없습니다.";
      } else {
        blocked = "1표 이상 받은 장소가 있어야 확정할 수 있습니다.";
      }
    }

    List<CoordinationView.SlotView> slots = new ArrayList<>();
    Map<LocalDate, List<String>> byDay = new LinkedHashMap<>();
    for (LocalDateTime start : common) {
      String range = start.format(TIME) + " ~ " + start.plusHours(1).format(TIME);
      slots.add(
          new CoordinationView.SlotView(
              start.toString(), start.toLocalDate().format(DAY) + " " + range));
      byDay.computeIfAbsent(start.toLocalDate(), d -> new ArrayList<>()).add(range);
    }
    List<CoordinationView.DayView> days = new ArrayList<>();
    byDay.forEach((date, times) -> days.add(new CoordinationView.DayView(date.format(DAY), times)));

    return CoordinationView.builder()
        .meetingId(meetingId)
        .meetingName(meeting.getName())
        .confirmed(confirmed)
        .creator(creator)
        .memberCount(members.size())
        .respondedCount(members.size() - waiting.size())
        .pendingResponses(!waiting.isEmpty())
        .pendingNames(String.join(", ", waiting))
        .noCommonTime(waiting.isEmpty() && common.isEmpty())
        .slots(slots)
        .days(days)
        .placeCount(placeViews.size())
        .hasMyVote(myPlaceId != null)
        .places(placeViews)
        .confirmPlaces(topPlaces)
        .tie(topPlaces.size() > 1)
        .canConfirm(canConfirm)
        .hasConfirmBlockedReason(!blocked.isEmpty())
        .confirmBlockedReason(blocked)
        .build();
  }

  /** 확정 결과 화면. 모임 참여자만 볼 수 있고, 확정 전이면 confirmed=false 로 돌려준다. */
  public ResultView getResult(Long meetingId, String email) {
    Meeting meeting = access.requireMember(meetingId, email, false);
    boolean done =
        meeting.getStatus() == MeetingStatus.CONFIRMED
            && meeting.getConfirmedDate() != null
            && meeting.getConfirmedStartTime() != null
            && meeting.getConfirmedEndTime() != null
            && meeting.getConfirmedPlace() != null;
    if (!done) {
      return ResultView.builder()
          .meetingId(meetingId)
          .meetingName(meeting.getName())
          .confirmed(false)
          .dateLabel("")
          .timeLabel("")
          .placeName("")
          .placeMemo("")
          .hasPlaceMemo(false)
          .build();
    }
    Place place = meeting.getConfirmedPlace();
    String memo = place.getMemo() == null ? "" : place.getMemo();
    return ResultView.builder()
        .meetingId(meetingId)
        .meetingName(meeting.getName())
        .confirmed(true)
        .dateLabel(meeting.getConfirmedDate().format(FULL_DAY))
        .timeLabel(
            meeting.getConfirmedStartTime().format(TIME)
                + " ~ "
                + meeting.getConfirmedEndTime().format(TIME))
        .placeName(place.getName())
        .placeMemo(memo)
        .hasPlaceMemo(!memo.isBlank())
        .build();
  }

  // ---------------------------------------------------------------- 쓰기

  /** 장소 후보 등록. 참여자만, 확정 전에만 가능하다. */
  @Transactional
  public Long addPlace(Long meetingId, String email, PlaceCreateForm form) {
    Meeting meeting = access.requireMember(meetingId, email, true);
    User user = access.currentUser(email);

    String name = form.getName() == null ? "" : form.getName().strip();
    if (name.isEmpty()) {
      throw new BusinessException("장소 이름을 입력해주세요.");
    }
    String memo = form.getMemo() == null ? "" : form.getMemo().strip();

    Place place = new Place();
    place.setMeeting(meeting);
    place.setName(name);
    place.setMemo(memo.isEmpty() ? null : memo);
    place.setCreatedBy(user);
    return placeRepository.save(place).getId();
  }

  /** 투표. 모임당 1인 1표이고, 이미 투표했다면 같은 표의 장소만 바꾼다. */
  @Transactional
  public void castVote(Long meetingId, Long placeId, String email) {
    Meeting meeting = access.requireMember(meetingId, email, true);
    User user = access.currentUser(email);
    // 다른 모임의 장소로는 투표할 수 없다.
    Place place =
        placeRepository
            .findByIdAndMeetingId(placeId, meetingId)
            .orElseThrow(() -> new BusinessException("이 모임의 장소 후보가 아닙니다."));

    Optional<Vote> existing = voteRepository.findByMeetingIdAndUserId(meetingId, user.getId());
    if (existing.isPresent()) {
      // 표 변경: 기존 행의 장소만 바꾼다. (삭제 후 추가하면 같은 트랜잭션 안에서 추가가 먼저 실행되어
      // 유니크 제약(uk_vote)에 걸린다.)
      existing.get().setPlace(place);
      return;
    }
    Vote vote = new Vote();
    vote.setMeeting(meeting);
    vote.setUser(user);
    vote.setPlace(place);
    voteRepository.save(vote);
  }

  /**
   * 팀장 확정. 날짜·시작·종료 시각·장소와 CONFIRMED 상태를 한 트랜잭션에서 저장한다.
   *
   * @param slot 전원 가능한 시간 슬롯의 시작 시각. 예: 2026-11-12T15:00
   */
  @Transactional
  public void confirm(Long meetingId, String email, Long placeId, String slot) {
    Meeting meeting = access.requireMember(meetingId, email, true); // 잠금 + 참여자 + OPEN
    User user = access.currentUser(email);
    if (!meeting.getCreator().getId().equals(user.getId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "팀장만 모임을 확정할 수 있습니다.");
    }

    List<MeetingMember> members = memberRepository.findAllByMeetingIdOrderByIdAsc(meetingId);
    List<String> waiting = waitingNames(members);
    if (!waiting.isEmpty()) {
      throw new BusinessException(
          "아직 응답하지 않은 참여자가 있어 확정할 수 없습니다. (" + String.join(", ", waiting) + ")");
    }

    LocalDateTime start = parseSlot(slot);
    if (!findCommonSlots(meetingId, members).contains(start)) {
      throw new BusinessException("전원이 가능한 시간만 선택할 수 있습니다.");
    }

    Place place =
        placeRepository
            .findByIdAndMeetingId(placeId, meetingId)
            .orElseThrow(() -> new BusinessException("이 모임의 장소 후보가 아닙니다."));
    Map<Long, Long> counts = voteCounts(meetingId);
    long maxVotes = counts.values().stream().mapToLong(Long::longValue).max().orElse(0L);
    if (maxVotes < 1) {
      throw new BusinessException("1표 이상 받은 장소가 있어야 확정할 수 있습니다.");
    }
    if (counts.getOrDefault(place.getId(), 0L) != maxVotes) {
      throw new BusinessException("최다 득표 장소만 확정할 수 있습니다.");
    }

    meeting.setConfirmedDate(start.toLocalDate());
    meeting.setConfirmedStartTime(start.toLocalTime());
    meeting.setConfirmedEndTime(start.toLocalTime().plusHours(1));
    meeting.setConfirmedPlace(place);
    meeting.setStatus(MeetingStatus.CONFIRMED);
  }

  // ---------------------------------------------------------------- 내부 계산

  private LocalDateTime parseSlot(String slot) {
    try {
      return LocalDateTime.parse(slot);
    } catch (DateTimeParseException | NullPointerException e) {
      throw new BusinessException("선택한 시간 형식이 올바르지 않습니다.");
    }
  }

  /** respondedAt 이 null 인 참여자 이름 (미응답자). */
  private List<String> waitingNames(List<MeetingMember> members) {
    return members.stream()
        .filter(m -> m.getRespondedAt() == null)
        .map(m -> m.getUser().getName())
        .toList();
  }

  private Map<Long, Long> voteCounts(Long meetingId) {
    Map<Long, Long> counts = new HashMap<>();
    for (Vote v : voteRepository.findByMeetingId(meetingId)) {
      counts.merge(v.getPlace().getId(), 1L, Long::sum);
    }
    return counts;
  }

  /**
   * 전체 참여자가 모두 가능한 1시간 슬롯. 후보 날짜 순, 시간 순으로 돌려준다. 시작 시각은 09~20시 정각만 인정한다.
   */
  private List<LocalDateTime> findCommonSlots(Long meetingId, List<MeetingMember> members) {
    if (members.isEmpty()) {
      return List.of();
    }
    Set<Long> memberIds = new HashSet<>();
    for (MeetingMember m : members) {
      memberIds.add(m.getId());
    }

    // (후보 날짜 id, 시작 시) 별로 가능하다고 답한 참여자 id 모음
    Map<String, Set<Long>> available = new HashMap<>();
    for (Availability a : availabilityRepository.findAllByMemberMeetingId(meetingId)) {
      LocalTime t = a.getStartTime();
      if (t.getMinute() != 0 || t.getSecond() != 0 || t.getHour() < FIRST_HOUR || t.getHour() > LAST_HOUR) {
        continue;
      }
      Long memberId = a.getMember().getId();
      if (!memberIds.contains(memberId)) {
        continue;
      }
      available
          .computeIfAbsent(a.getMeetingDate().getId() + "|" + t.getHour(), k -> new HashSet<>())
          .add(memberId);
    }

    List<LocalDateTime> result = new ArrayList<>();
    for (MeetingDate date : dateRepository.findAllByMeetingIdOrderByDateAsc(meetingId)) {
      for (int hour = FIRST_HOUR; hour <= LAST_HOUR; hour++) {
        Set<Long> who = available.get(date.getId() + "|" + hour);
        if (who != null && who.size() == memberIds.size()) {
          result.add(LocalDateTime.of(date.getDate(), LocalTime.of(hour, 0)));
        }
      }
    }
    return result;
  }
}
