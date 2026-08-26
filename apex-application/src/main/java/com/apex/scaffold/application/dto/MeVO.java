package com.apex.scaffold.application.dto;

import com.apex.scaffold.domain.enums.EducationLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

/**
 * 当前用户信息视图对象（MeVO）
 *
 * <p>用于展示当前登录用户的基本信息，包含用户ID、昵称、性别、生日、青少年模式状态等
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class MeVO {

    /**
     * 用户ID
     *
     * <p>系统内部用户唯一标识
     */
    @Schema(description = "用户ID")
    private String userId;

    /**
     * 昵称
     *
     * <p>用户的微信昵称或自定义昵称
     */
    @Schema(description = "昵称")
    private String nickName;

    /**
     * 头像地址。
     */
    @Schema(description = "头像地址")
    private String avatarUrl;

    /**
     * 是否展示 ApexVIP 标识。
     */
    @Schema(description = "是否为有效 ApexVIP 会员")
    private Boolean apexVip;

    /**
     * 当前会员等级：NONE、VIP 或 SVIP。
     */
    @Schema(description = "当前会员等级：NONE、VIP 或 SVIP")
    private String vipLevel;

    /**
     * 认证状态：INACTIVE（未完成）或 COMPLETED（已完成）。
     */
    @Schema(description = "认证状态：INACTIVE 或 COMPLETED")
    private String verificationStatus;

    /**
     * 已完成认证样本数，最大为认证所需数量。
     */
    @Schema(description = "已完成认证样本数，最大为 verificationRequiredCount")
    private Integer verificationCompletedCount;

    /**
     * 完成认证所需样本数。
     */
    @Schema(description = "完成认证所需样本数")
    private Integer verificationRequiredCount;

    /**
     * 用户累计收到的有效偏爱（点赞）数。
     */
    @Schema(description = "累计获赞数")
    private Long receivedLikeCount;

    /**
     * 用户当前算力账户余额。
     */
    @Schema(description = "用户当前算力账户余额")
    private Long creditBalance;

    /**
     * 用户收到的有效审美共鸣数量。
     */
    @Schema(description = "用户收到的有效审美共鸣数")
    private Long aestheticResonanceCount;

    /**
     * 用户已发布照片剩余曝光算力总和。
     */
    @Schema(description = "已发布照片的剩余曝光算力总和")
    private Long exposureCreditBalance;

    /**
     * 性别
     *
     * <p>0-未知，1-男，2-女
     */
    @Schema(description = "性别")
    private Integer gender;

    /**
     * 生日
     *
     * <p>用户的出生日期，格式为YYYY-MM-DD
     */
    @Schema(description = "生日")
    private String birthday;

    /** 常住城市，也是推荐城市硬门禁的数据来源。 */
    @Schema(description = "常住城市", example = "上海")
    private String city;

    /** 身高，单位厘米，也是推荐身高硬门禁的数据来源。 */
    @Schema(description = "身高(cm)", minimum = "100", maximum = "250", example = "168")
    private Integer heightCm;

    /**
     * 最高教育程度。
     *
     * <p>未填写时为 {@code null}。枚举编码及中文含义：HIGH_SCHOOL（高中及以下）、
     * ASSOCIATE（大专）、BACHELOR（本科）、MASTER（硕士）、DOCTOR（博士）、OTHER（其他）。</p>
     */
    @Schema(
            description =
                    "最高教育程度；可选值 HIGH_SCHOOL（高中及以下）、ASSOCIATE（大专）、" + "BACHELOR（本科）、MASTER（硕士）、DOCTOR（博士）、OTHER（其他）",
            allowableValues = {"HIGH_SCHOOL", "ASSOCIATE", "BACHELOR", "MASTER", "DOCTOR", "OTHER"},
            example = "BACHELOR",
            nullable = true)
    private EducationLevel educationLevel;

    /**
     * 是否青少年模式
     *
     * <p>true-已开启青少年模式，false-正常模式
     */
    @Schema(description = "是否青少年模式")
    private Boolean teenMode;

    /**
     * 是否社交用户
     *
     * <p>true-已成为社交用户，false-普通用户
     *
     * <p>社交用户条件：收到投票 10 次并发起 1 次聊天。
     */
    @Schema(description = "是否社交用户")
    private Boolean socialUser;

    /**
     * 同频相关信息
     *
     * <p>包含同频开启状态、匹配偏好等
     */
    @Schema(description = "同频相关信息")
    private Map<String, Object> sameFrequency;

    /**
     * 标签信息
     *
     * <p>包含性格标签、兴趣标签等
     */
    @Schema(description = "标签信息")
    private Map<String, Object> tags;

    /**
     * 手机号
     *
     * <p>用户的手机号码，需用户授权后获取
     */
    @Schema(description = "手机号")
    private String phone;

    /**
     * 微信OpenID
     *
     * <p>用户在微信开放平台的唯一标识
     */
    @Schema(description = "微信OpenID")
    private String openId;

    /**
     * 微信UnionID（可选）
     *
     * <p>同一主体下的唯一标识，需要绑定微信开放平台账号
     */
    @Schema(description = "微信UnionID")
    private String unionId;
}
