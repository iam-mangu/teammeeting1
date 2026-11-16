package com.example.teammeeting.meeting;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
  Optional<Meeting> findByInviteCode(String inviteCode);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Meeting m where m.inviteCode = :code")
  Optional<Meeting> findByInviteCodeForUpdate(@Param("code") String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Meeting m where m.id = :id")
  Optional<Meeting> findByIdForUpdate(@Param("id") Long id);
}
