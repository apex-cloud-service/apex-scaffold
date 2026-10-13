package com.apex.scaffold.application.dto.admin;

import com.apex.scaffold.domain.enums.EducationLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 后台展示的用户画像明细。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "AdminUserProfileResponse", description = "后台展示的用户画像明细响应")
public record AdminUserProfileResponse(
        @Schema(description = "交友目标", example = "认真恋爱") String datingGoal,
        @Schema(
                        description =
                                "MBTI 人格类型（INTJ、INTP、ENTJ、ENTP、INFJ、INFP、ENFJ、ENFP、ISTJ、ISFJ、ESTJ、ESFJ、ISTP、ISFP、ESTP、ESFP，"
                                        + "可为空表示未填写）",
                        example = "INTJ")
                String mbti,
        @Schema(description = "身高（厘米）", example = "175") Integer heightCm,
        @Schema(description = "学校", example = "清华大学") String school,
        @Schema(
                        description = "最高学历（HIGH_SCHOOL-高中及以下，ASSOCIATE-大专，BACHELOR-本科，MASTER-硕士，DOCTOR-博士，OTHER-其他）",
                        example = "BACHELOR")
                EducationLevel educationLevel,
        @Schema(description = "职业", example = "软件工程师") String job,
        @Schema(description = "个人简介", example = "喜欢摄影和旅行") String bio,
        @Schema(description = "自定义兴趣标签（JSON 数组字符串）", example = "[\"篮球\",\"摄影\",\"旅行\"]") String interests,
        @Schema(description = "是否为付费社交用户", example = "true") Boolean socialUser,
        @Schema(description = "成为社交用户的时间", example = "2026-06-01T12:00:00") LocalDateTime socialUserAt) {}
