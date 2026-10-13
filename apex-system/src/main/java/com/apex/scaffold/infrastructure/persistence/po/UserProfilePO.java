package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.apex.scaffold.domain.enums.EducationLevel;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 用户资料扩展持久化对象。
 *
 * <p>映射数据库表 biz_user_profile，存储用户的扩展资料信息，
 * 包括交友目的、MBTI、身高、学校、职业、简介及社交用户标识等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("biz_user_profile")
public class UserProfilePO extends BasePO {

    /**
     * 用户 ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 交友目的。
     */
    @TableField("dating_goal")
    private String datingGoal;

    /**
     * MBTI 人格类型。
     */
    @TableField("mbti")
    private String mbti;

    /**
     * 身高（厘米）。
     */
    @TableField("height_cm")
    private Integer heightCm;

    /**
     * 学校。
     */
    @TableField("school")
    private String school;

    /**
     * 最高教育程度。
     *
     * <p>MyBatis 使用枚举名称写入 {@code VARCHAR(32)} 列，读取时按名称恢复；历史记录为
     * {@code NULL} 时表示用户尚未填写该字段。</p>
     */
    @TableField("education_level")
    private EducationLevel educationLevel;

    /**
     * 职业。
     */
    @TableField("job")
    private String job;

    /**
     * 个人简介。
     */
    @TableField("bio")
    private String bio;

    /**
     * 自定义兴趣标签（JSON数组格式）。
     *
     * <p>P0-12修复(FE-2): 新增字段，映射数据库列 interests，存储JSON格式的自定义兴趣标签列表。
     *
     * @since 2.1.0
     */
    @TableField("interests")
    private String interests;

    /**
     * 是否社交用户（0 表示否，1 表示是）。
     */
    @TableField("social_user")
    private Integer socialUser;

    /**
     * 成为社交用户的时间。
     */
    @TableField("social_user_at")
    private LocalDateTime socialUserAt;
}
