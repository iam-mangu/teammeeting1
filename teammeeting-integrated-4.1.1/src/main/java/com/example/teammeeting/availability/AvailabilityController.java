package com.example.teammeeting.availability;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AvailabilityController {
  private final AvailabilityService availability;

  public AvailabilityController(AvailabilityService s) {
    availability = s;
  }

  @PostMapping("/meetings/{meetingId}/availability")
  public String save(
      @PathVariable Long meetingId,
      Principal user,
      @ModelAttribute AvailabilityForm form,
      RedirectAttributes flash) {
    availability.save(meetingId, user.getName(), form.getSlots(), false);
    flash.addFlashAttribute("notice", "가능한 시간을 저장했습니다.");
    return "redirect:/meetings/" + meetingId;
  }

  @PostMapping("/meetings/{meetingId}/availability/none")
  public String none(@PathVariable Long meetingId, Principal user, RedirectAttributes flash) {
    availability.save(meetingId, user.getName(), java.util.List.of(), true);
    flash.addFlashAttribute("notice", "가능 시간 없음으로 응답했습니다.");
    return "redirect:/meetings/" + meetingId;
  }
}
