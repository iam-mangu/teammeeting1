package com.example.teammeeting.meeting;

import com.example.teammeeting.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "meeting_members",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_member",
          columnNames = {"meeting_id", "user_id"})
    })
@Getter
@Setter
@NoArgsConstructor
public class MeetingMember {
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

  @Column(name = "responded_at")
  private LocalDateTime respondedAt;
}
