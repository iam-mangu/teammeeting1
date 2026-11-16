package com.example.teammeeting.coordination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaceCreateForm {

    @NotBlank(message = "장소 이름을 입력해주세요.")
    @Size(max = 80, message = "장소 이름은 80자 이내여야 합니다.")
    private String name;

    @Size(max = 300, message = "메모는 300자 이내여야 합니다.")
    private String memo;
}