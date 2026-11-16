package com.example.teammeeting.user;

import com.example.teammeeting.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public AccountService(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @Transactional
  public User signup(SignupForm form) {
    String email = form.getEmail().strip().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email)) throw new BusinessException("이미 가입된 이메일입니다.");
    if (form.getPassword().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BusinessException("비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요.");
    User user = new User();
    user.setEmail(email);
    user.setName(form.getName().strip());
    user.setPasswordHash(encoder.encode(form.getPassword()));
    return users.saveAndFlush(user);
  }
}
