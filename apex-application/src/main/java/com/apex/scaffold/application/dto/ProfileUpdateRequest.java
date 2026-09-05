package com.apex.scaffold.application.dto;

import com.apex.scaffold.domain.enums.EducationLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料更新请求DTO
 *
 * <p>用于更新用户的个人资料信息，包含昵称、头像、交友目的、自我介绍、MBTI、身高、学校、职业、标签等
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequest {

    /** 性别编码，与系统用户字段保持一致：1 男，2 女。 */
    @Schema(
            description = "性别编码：1=男，2=女；省略表示不修改",
            allowableValues = {"1", "2"},
            example = "2")
    @Min(value = 1, message = "性别编码只能是1或2")
    @Max(value = 2, message = "性别编码只能是1或2")
    private Integer gender;

    /** 出生日期，用于展示年龄以及推荐年龄硬门禁。 */
    @Schema(description = "出生日期，格式 yyyy-MM-dd；年龄必须在18至100岁之间", type = "string", format = "date", example = "1998-08-06")
    private LocalDate birthday;

    /** 常住城市，使用产品统一城市名称。 */
    @Schema(description = "常住城市；也是推荐城市硬门禁的数据来源", example = "上海")
    @Size(max = 64, message = "城市名称不能超过64个字符")
    private String city;

    /**
     * 昵称
     *
     * <p>用户的显示名称
     */
    @Schema(description = "昵称")
    @Size(max = 64, message = "{param.size_invalid}")
    private String nickName;

    /** 用户头像 URL。 */
    @Schema(description = "头像URL")
    @Size(max = 2048, message = "头像URL不能超过2048个字符")
    private String avatarUrl;

    /**
     * 交友目的
     *
     * <p>用户的交友目标，如交朋友、寻找伴侣等
     */
    @Schema(description = "交友目标")
    @Size(max = 64, message = "{param.size_invalid}")
    private String datingGoal;

    /**
     * 个人简介
     *
     * <p>用户的自我介绍或个人描述
     */
    @Schema(description = "个人简介")
    @Size(max = 512, message = "{param.size_invalid}")
    private String bio;

    /**
     * MBTI类型
     *
     * <p>用户的MBTI性格类型，如INTJ、ENFP等
     */
    @Schema(description = "MBTI类型")
    @Size(max = 8, message = "{param.size_invalid}")
    private String mbti;

    /**
     * 身高（厘米）
     *
     * <p>用户的身高，单位为厘米
     */
    @Schema(description = "身高(cm)")
    @Min(value = 100, message = "身高不能低于100厘米")
    @Max(value = 250, message = "身高不能高于250厘米")
    private Integer heightCm;

    /**
     * 学校
     *
     * <p>用户的教育背景，就读或毕业的学校名称
     */
    @Schema(description = "学校")
    @Size(max = 128, message = "{param.size_invalid}")
    private String school;

    /**
     * 最高教育程度。
     *
     * <p>使用稳定枚举编码，取值为 {@code HIGH_SCHOOL}、{@code ASSOCIATE}、
     * {@code BACHELOR}、{@code MASTER}、{@code DOCTOR} 或 {@code OTHER}。
     * 省略该字段表示本次 PATCH 不修改学历；传 {@code null} 无法通过 JSON 区分省略时，
     * 请使用资料清空接口（若产品后续提供）处理清空语义。</p>
     */
    @Schema(
            description =
                    "最高教育程度。可选值：HIGH_SCHOOL（高中及以下）、ASSOCIATE（大专）、" + "BACHELOR（本科）、MASTER（硕士）、DOCTOR（博士）、OTHER（其他）",
            allowableValues = {"HIGH_SCHOOL", "ASSOCIATE", "BACHELOR", "MASTER", "DOCTOR", "OTHER"},
            example = "BACHELOR",
            nullable = true)
    private EducationLevel educationLevel;

    /**
     * 职业
     *
     * <p>用户的职业或工作单位
     */
    @Schema(description = "职业")
    @Size(max = 128, message = "{param.size_invalid}")
    private String job;

    /**
     * 性格标签列表
     *
     * <p>用户自选的性格标签，如开朗、内向等
     */
    @Schema(description = "个性标签")
    private List<String> personalityTags;

    /**
     * 兴趣标签列表
     *
     * <p>用户的兴趣爱好标签，如音乐、运动、旅行等
     */
    @Schema(description = "兴趣标签")
    private List<String> interestTags;

    /**
     * 品质标签列表
     *
     * <p>用户看重的品质标签，如诚实、善良等
     */
    @Schema(description = "品质标签")
    private List<String> qualityTags;

    /** 若本次传入生日，则必须满足产品允许的成人年龄范围。 */
    @AssertTrue(message = "年龄必须在18至100岁之间")
    public boolean isBirthdayInSupportedRange() {
        if (birthday == null) return true;
        LocalDate today = LocalDate.now();
        return !birthday.isAfter(today.minusYears(18))
                && !birthday.isBefore(today.minusYears(101).plusDays(1));
    }
}
