package com.example.teammeeting.meeting;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "meeting_dates",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_meeting_date",
          columnNames = {"meeting_id", "candidate_date"})
    })
@Getter
@Setter
@NoArgsConstructor
public class MeetingDate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "meeting_id", nullable = false)
  private Meeting meeting;

  @Column(name = "candidate_date", nullable = false)
  private LocalDate date;
}
