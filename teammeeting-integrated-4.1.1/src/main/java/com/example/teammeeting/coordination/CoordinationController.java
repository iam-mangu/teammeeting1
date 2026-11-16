package com.example.teammeeting.coordination;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * B 담당 화면. 요청 주소는 docs/B_개발_시작.md 의 표와 같다.
 *
 * <pre>
 * GET  /meetings/{meetingId}/coordination   장소 후보·투표·확정 화면
 * POST /meetings/{meetingId}/places         장소 후보 등록
 * POST /meetings/{meetingId}/votes          투표 (표 변경 포함)
 * POST /meetings/{meetingId}/confirm        팀장 확정 (시간 + 장소)
 * GET  /meetings/{meetingId}/result         확정 결과
 * </pre>
 *
 * Principal.getName() 은 소문자로 정규화된 로그인 이메일이다.
 */
@Controller
@RequestMapping("/meetings/{meetingId}")
@RequiredArgsConstructor
public class CoordinationController {

  private final CoordinationService coordinationService;

  @GetMapping("/coordination")
  public String coordination(@PathVariable Long meetingId, Principal principal, Model model) {
    fillPage(model, meetingId, principal, "", "");
    return "coordination/index";
  }

  @PostMapping("/places")
  public String addPlace(
      @PathVariable Long meetingId,
      Principal principal,
      @Valid PlaceCreateForm form,
      BindingResult bindingResult,
      Model model) {
    if (bindingResult.hasErrors()) {
      // 입력 오류: 입력값을 그대로 채워 같은 화면을 다시 보여준다.
      fillPage(model, meetingId, principal, nullToEmpty(form.getName()), nullToEmpty(form.getMemo()));
      List<String> errors =
          bindingResult.getAllErrors().stream().map(e -> e.getDefaultMessage()).toList();
      model.addAttribute("formErrors", errors);
      return "coordination/index";
    }
    coordinationService.addPlace(meetingId, principal.getName(), form);
    return "redirect:/meetings/" + meetingId + "/coordination";
  }

  @PostMapping("/votes")
  public String vote(
      @PathVariable Long meetingId, @RequestParam Long placeId, Principal principal) {
    coordinationService.castVote(meetingId, placeId, principal.getName());
    return "redirect:/meetings/" + meetingId + "/coordination";
  }

  @PostMapping("/confirm")
  public String confirm(
      @PathVariable Long meetingId,
      @RequestParam Long placeId,
      @RequestParam String slot,
      Principal principal) {
    coordinationService.confirm(meetingId, principal.getName(), placeId, slot);
    return "redirect:/meetings/" + meetingId + "/result";
  }

  @GetMapping("/result")
  public String result(@PathVariable Long meetingId, Principal principal, Model model) {
    model.addAttribute("result", coordinationService.getResult(meetingId, principal.getName()));
    return "coordination/result";
  }

  private void fillPage(
      Model model, Long meetingId, Principal principal, String formName, String formMemo) {
    model.addAttribute(
        "coordination", coordinationService.getCoordination(meetingId, principal.getName()));
    model.addAttribute("formName", formName);
    model.addAttribute("formMemo", formMemo);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
