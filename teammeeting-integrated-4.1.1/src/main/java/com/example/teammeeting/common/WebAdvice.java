package com.example.teammeeting.common;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class WebAdvice {
  @ModelAttribute
  public void common(Model model, HttpServletRequest request, Principal principal) {
    model.addAttribute("signedIn", principal != null);
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (token != null)
      model.addAttribute(
          "csrf", Map.of("name", token.getParameterName(), "token", token.getToken()));
  }

  @ExceptionHandler(BusinessException.class)
  public ModelAndView business(BusinessException e) {
    return error(e.getStatus(), e.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ModelAndView forbidden(AccessDeniedException e) {
    return error(HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 없습니다.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ModelAndView duplicate(DataIntegrityViolationException e) {
    return error(HttpStatus.CONFLICT, "중복되거나 올바르지 않은 요청입니다.");
  }

  private ModelAndView error(HttpStatus status, String message) {
    ModelAndView view = new ModelAndView("error");
    view.setStatus(status);
    view.addObject("message", message);
    return view;
  }
}
