package com.example.teammeeting.user;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupForm {
  @NotBlank
  @Email
  @Size(max = 254)
  private String email;

  @NotBlank
  @Size(min = 2, max = 40)
  private String name;

  @NotBlank
  @Size(min = 8, max = 60)
  private String password;
}
