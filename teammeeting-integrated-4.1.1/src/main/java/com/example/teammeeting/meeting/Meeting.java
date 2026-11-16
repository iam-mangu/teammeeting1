package com.example.teammeeting.meeting;

import com.example.teammeeting.coordination.Place;
import com.example.teammeeting.user.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "meetings",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_meeting_code",
          columnNames = {"invite_code"})
    })
@Getter
@Setter
@NoArgsConstructor
public class Meeting {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @Column(name = "name", nullable = false, length = 80)
  private String name;

  @Column(name = "invite_code", nullable = false, length = 16)
  private String inviteCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "creator_id", nullable = false)
  private User creator;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private MeetingStatus status = MeetingStatus.OPEN;

  @Column(name = "confirmed_date")
  private LocalDate confirmedDate;

  @Column(name = "confirmed_start_time")
  private LocalTime confirmedStartTime;

  @Column(name = "confirmed_end_time")
  private LocalTime confirmedEndTime;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "confirmed_place_id")
  private Place confirmedPlace;
}
