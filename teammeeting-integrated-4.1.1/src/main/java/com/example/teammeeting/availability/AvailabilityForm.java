package com.example.teammeeting.availability;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvailabilityForm {
  private List<String> slots = new ArrayList<>();
}
