package com.apex.scaffold.application.dto.admin;

import com.apex.scaffold.domain.enums.EducationLevel;
import java.time.LocalDateTime;

/**
 * 后台展示的用户画像明细。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record AdminUserProfileResponse(
        String datingGoal,
        String mbti,
        Integer heightCm,
        String school,
        EducationLevel educationLevel,
        String job,
        String bio,
        String interests,
        Boolean socialUser,
        LocalDateTime socialUserAt) {}
