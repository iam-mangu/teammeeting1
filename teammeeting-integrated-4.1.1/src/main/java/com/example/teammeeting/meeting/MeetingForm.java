package com.example.teammeeting.meeting;

import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeetingForm {
  @NotBlank
  @Size(max = 80)
  private String name;

  @NotEmpty
  @Size(max = 7)
  private List<String> dates = new ArrayList<>();
}
