package com.example.teammeeting.coordination;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 장소·투표 화면(coordination/index.mustache)에 필요한 값만 모은 읽기 전용 모델.
 *
 * <p>엔티티를 화면에 그대로 넘기지 않고 Service 트랜잭션 안에서 이 모델로 바꿔 넘긴다. (open-in-view가 꺼져 있어도 지연 로딩
 * 오류가 나지 않는다.) Mustache는 값이 null이면 오류가 나므로 문자열 필드는 항상 빈 문자열 이상으로 채운다.
 */
@Getter
@Builder
public class CoordinationView {
  private final Long meetingId;
  private final String meetingName;
  private final boolean confirmed;
  private final boolean creator;

  private final int memberCount;
  private final int respondedCount;
  /** 아직 가능 시간을 응답하지 않은 참여자가 있다. */
  private final boolean pendingResponses;

  private final String pendingNames;
  /** 전원이 응답했지만 전원이 가능한 시간이 없다. */
  private final boolean noCommonTime;

  /** 확정 폼의 시간 선택지 (전원 가능한 1시간 슬롯). */
  private final List<SlotView> slots;
  /** 화면 표시용 날짜별 묶음. */
  private final List<DayView> days;

  private final int placeCount;
  private final boolean hasMyVote;
  private final List<PlaceView> places;
  /** 확정 폼의 장소 선택지 (1표 이상 받은 최다 득표 장소. 동률이면 여러 개). */
  private final List<PlaceView> confirmPlaces;

  private final boolean tie;
  private final boolean canConfirm;
  private final boolean hasConfirmBlockedReason;
  private final String confirmBlockedReason;

  @Getter
  @AllArgsConstructor
  public static class SlotView {
    /** 요청 값. 예: 2026-11-12T15:00 */
    private final String value;
    /** 화면 표시. 예: 11월 12일 (목) 15:00 ~ 16:00 */
    private final String label;
  }

  @Getter
  @AllArgsConstructor
  public static class DayView {
    private final String dateLabel;
    private final List<String> times;
  }

  @Getter
  @Builder
  public static class PlaceView {
    private final Long id;
    private final String name;
    private final String memo;
    private final boolean hasMemo;
    private final String createdByName;
    private final long voteCount;
    /** 내가 투표한 장소 */
    private final boolean mine;
    /** 1표 이상 받은 최다 득표 장소 */
    private final boolean top;
  }
}
