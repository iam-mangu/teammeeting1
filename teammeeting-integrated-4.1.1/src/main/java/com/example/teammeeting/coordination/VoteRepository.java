package com.example.teammeeting.coordination;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, Long> {
  // 특정 모임의 모든 투표 내역 조회 (득표수 집계용)
  List<Vote> findByMeetingId(Long meetingId);

  // 사용자가 해당 모임에서 투표한 내역 조회 (1인 1표 확인, 표 변경용)
  Optional<Vote> findByMeetingIdAndUserId(Long meetingId, Long userId);
}
