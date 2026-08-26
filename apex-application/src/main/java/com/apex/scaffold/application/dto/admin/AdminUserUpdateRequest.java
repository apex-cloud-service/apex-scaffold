package com.apex.scaffold.application.dto.admin;

import com.apex.scaffold.domain.enums.EducationLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 超级管理员编辑用户基础资料和画像请求。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record AdminUserUpdateRequest(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
        @NotBlank @Size(max = 50) String nickname,
        @NotNull @Min(1) @Max(2) Integer gender,
        @NotNull @Past LocalDate birthday,
        @Size(max = 100) String country,
        @Size(max = 100) String province,
        @Size(max = 100) String city,
        @Size(max = 64) String datingGoal,
        @Pattern(regexp = "^(|[EI][NS][TF][JP])$") String mbti,
        @Min(100) @Max(250) Integer heightCm,
        @Size(max = 100) String school,
        EducationLevel educationLevel,
        @Size(max = 100) String job,
        @Size(max = 500) String bio) {}
