package com.example.teammeeting.availability;

import com.example.teammeeting.meeting.MeetingDate;
import com.example.teammeeting.meeting.MeetingMember;
import jakarta.persistence.*;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "availabilities",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_availability",
          columnNames = {"member_id", "meeting_date_id", "start_time"})
    })
@Getter
@Setter
@NoArgsConstructor
public class Availability {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private MeetingMember member;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "meeting_date_id", nullable = false)
  private MeetingDate meetingDate;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;
}
