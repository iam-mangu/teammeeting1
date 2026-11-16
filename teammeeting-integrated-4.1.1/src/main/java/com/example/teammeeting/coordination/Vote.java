package com.example.teammeeting.coordination;

import com.example.teammeeting.meeting.Meeting;
import com.example.teammeeting.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "votes",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_vote",
          columnNames = {"meeting_id", "user_id"})
    })
@Getter
@Setter
@NoArgsConstructor
public class Vote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "meeting_id", nullable = false)
  private Meeting meeting;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "place_id", nullable = false)
  private Place place;
}
