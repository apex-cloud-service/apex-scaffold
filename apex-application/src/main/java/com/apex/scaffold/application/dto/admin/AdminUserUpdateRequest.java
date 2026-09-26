package com.apex.scaffold.application.dto.admin;

import com.apex.scaffold.domain.enums.EducationLevel;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "AdminUserUpdateRequest", description = "超级管理员编辑用户基础资料和画像请求（认证令牌等安全字段不可修改）")
public record AdminUserUpdateRequest(
        @NotBlank
                @Pattern(regexp = "^1[3-9]\\d{9}$")
                @Schema(
                        description = "用户手机号（大陆 11 位手机号，不可与其他用户重复）",
                        example = "13800001234",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String phone,
        @NotBlank
                @Size(max = 50)
                @Schema(description = "用户昵称", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
                String nickname,
        @NotNull
                @Min(1)
                @Max(2)
                @Schema(
                        description = "性别（1-男，2-女）",
                        example = "1",
                        allowableValues = {"1", "2"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer gender,
        @NotNull
                @Past
                @Schema(
                        description = "出生日期（必须早于当前日期）",
                        example = "1998-06-15",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                LocalDate birthday,
        @Schema(description = "国家/地区", example = "中国") String country,
        @Schema(description = "省份", example = "广东省") String province,
        @Schema(description = "城市", example = "深圳市") String city,
        @Schema(description = "交友目标", example = "认真恋爱") String datingGoal,
        @Pattern(regexp = "^(|[EI][NS][TF][JP])$")
                @Schema(
                        description =
                                "MBTI 人格类型（INTJ、INTP、ENTJ、ENTP、INFJ、INFP、ENFJ、ENFP、ISTJ、ISFJ、ESTJ、ESFJ、ISTP、ISFP、ESTP、ESFP，"
                                        + "空串表示未填写）",
                        example = "INTJ")
                String mbti,
        @Min(100) @Max(250) @Schema(description = "身高（厘米，100-250）", example = "175") Integer heightCm,
        @Schema(description = "学校", example = "清华大学") String school,
        @Schema(
                        description = "最高学历（HIGH_SCHOOL-高中及以下，ASSOCIATE-大专，BACHELOR-本科，MASTER-硕士，DOCTOR-博士，OTHER-其他）",
                        example = "BACHELOR")
                EducationLevel educationLevel,
        @Schema(description = "职业", example = "软件工程师") String job,
        @Schema(description = "个人简介", example = "喜欢摄影和旅行") String bio) {}
