package com.example.teammeeting.meeting;

import com.example.teammeeting.common.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MeetingController {
  private final MeetingService meetings;
  private final PageDataService pages;
  private final Clock clock;

  public MeetingController(MeetingService m, PageDataService p, Clock c) {
    meetings = m;
    pages = p;
    clock = c;
  }

  @GetMapping("/meetings")
  public String list(Principal user, Model model) {
    model.addAllAttributes(pages.list(user.getName()));
    return "meetings/list";
  }

  @GetMapping("/meetings/new")
  public String create(Model model) {
    model.addAttribute("today", LocalDate.now(clock).toString());
    return "meetings/new";
  }

  @PostMapping("/meetings")
  public String create(
      Principal user, @Valid @ModelAttribute MeetingForm form, BindingResult errors, Model model) {
    model.addAttribute("today", LocalDate.now(clock).toString());
    if (errors.hasErrors()) {
      model.addAttribute("error", "모임 이름과 후보 날짜 1~7개를 입력해 주세요.");
      return "meetings/new";
    }
    try {
      return "redirect:/meetings/" + meetings.create(user.getName(), form).getId();
    } catch (BusinessException e) {
      model.addAttribute("error", e.getMessage());
      return "meetings/new";
    }
  }

  @PostMapping("/meetings/join")
  public String join(
      Principal user, @RequestParam(defaultValue = "") String code, RedirectAttributes flash) {
    try {
      return "redirect:/meetings/" + meetings.join(user.getName(), code);
    } catch (BusinessException e) {
      flash.addFlashAttribute("error", e.getMessage());
      return "redirect:/meetings";
    }
  }

  @GetMapping("/meetings/{meetingId}")
  public String detail(Principal user, @PathVariable Long meetingId, Model model) {
    model.addAllAttributes(pages.detail(meetingId, user.getName()));
    return "meetings/detail";
  }
}
