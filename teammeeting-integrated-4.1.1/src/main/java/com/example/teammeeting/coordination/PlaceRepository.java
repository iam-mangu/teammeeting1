package com.example.teammeeting.coordination;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
  // 특정 모임에 등록된 장소 후보 목록 조회
  List<Place> findByMeetingIdOrderByIdAsc(Long meetingId);

  // 다른 모임의 장소로 투표·확정하는 요청을 막기 위해 모임까지 함께 조건으로 조회
  Optional<Place> findByIdAndMeetingId(Long id, Long meetingId);
}
