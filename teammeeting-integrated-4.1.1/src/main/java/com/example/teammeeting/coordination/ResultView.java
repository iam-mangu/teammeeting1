package com.example.teammeeting.coordination;

import lombok.Builder;
import lombok.Getter;

/** 확정 결과 화면(coordination/result.mustache)용 읽기 전용 모델. 문자열은 null 없이 채운다. */
@Getter
@Builder
public class ResultView {
  private final Long meetingId;
  private final String meetingName;
  private final boolean confirmed;
  private final String dateLabel;
  private final String timeLabel;
  private final String placeName;
  private final String placeMemo;
  private final boolean hasPlaceMemo;
}
