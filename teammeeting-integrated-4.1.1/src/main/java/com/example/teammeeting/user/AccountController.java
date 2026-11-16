package com.example.teammeeting.user;

import com.example.teammeeting.common.BusinessException;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class AccountController {
  private final AccountService accounts;

  public AccountController(AccountService a) {
    accounts = a;
  }

  @GetMapping("/")
  public String home(Principal p) {
    return "redirect:" + (p == null ? "/login" : "/meetings");
  }

  @GetMapping("/signup")
  public String signup(Model model) {
    model.addAttribute("signupEmail", "");
    model.addAttribute("signupName", "");
    return "signup";
  }

  @PostMapping("/signup")
  public String signup(@Valid @ModelAttribute SignupForm form, BindingResult errors, Model model) {
    model.addAttribute("signupEmail", form.getEmail() == null ? "" : form.getEmail());
    model.addAttribute("signupName", form.getName() == null ? "" : form.getName());
    if (errors.hasErrors()) {
      model.addAttribute("error", "이메일, 이름(2~40자), 비밀번호(8~60자)를 확인해 주세요.");
      return "signup";
    }
    try {
      accounts.signup(form);
    } catch (BusinessException | DataIntegrityViolationException e) {
      model.addAttribute(
          "error", e instanceof BusinessException ? e.getMessage() : "이미 가입된 이메일입니다.");
      return "signup";
    }
    return "redirect:/login?registered";
  }

  @GetMapping("/login")
  public String login(
      @RequestParam(required = false) String error,
      @RequestParam(required = false) String logout,
      @RequestParam(required = false) String registered,
      Model model) {
    model.addAttribute("loginError", error != null);
    model.addAttribute("loggedOut", logout != null);
    model.addAttribute("registered", registered != null);
    return "login";
  }
}
