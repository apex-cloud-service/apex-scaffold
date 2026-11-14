package com.apex.scaffold.domain.model.entity;

import com.apex.scaffold.domain.enums.EducationLevel;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 用户画像聚合根实体
 *
 * <p>
 * 存储用户的详细画像信息，包括交友目的、人格类型、身高学历等。
 * 与{@link UserEntity}为一对一关联关系，共同构成用户完整画像。
 * 采用 Builder 模式进行复杂对象的构建。
 * </p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>交友目的：用于匹配算法的关键输入</li>
 *   <li>MBTI：16型人格，用于性格匹配</li>
 *   <li>身高、学历、职业：基础筛选条件</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see UserEntity
 * @see BaseEntity
 */
@Getter
@lombok.Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UserProfileEntity extends BaseEntity {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     *
     * <p>
     * 关联的{@link UserEntity}的主键ID。
     * 确保与用户实体的一对一关系。
     * </p>
     *
     */
    private Long userId;

    /**
     * 交友目的
     *
     * <p>
     * 用户使用应用的交友目标描述。
     * 用于算法匹配和展示给其他用户。
     * </p>
     *
     * <p><strong>示例值</strong></p>
     * <ul>
     *   <li>"认真恋爱"</li>
     *   <li>"寻找灵魂伴侣"</li>
     *   <li>"扩大社交圈"</li>
     * </ul>
     */
    private String datingGoal;

    /**
     * MBTI人格类型
     *
     * <p>
     * 用户的16型人格测试结果。
     * 用于性格匹配和展示兼容性分析。
     * </p>
     *
     * <p><strong>有效值</strong></p>
     * <p>16种组合：INTJ、INTP、ENTJ、ENTP、INFJ、INFP、ENFJ、ENFP、ISTJ、ISFJ、ESTJ、ESFJ、ISTP、ISFP、ESTP、ESFP</p>
     */
    private String mbti;

    /**
     * 身高（厘米）
     *
     * <p>
     * 用户的身高，以厘米为单位。
     * 用于筛选条件和展示。
     * </p>
     *
     * <p><strong>取值范围</strong></p>
     * <p>通常为100-250之间的整数</p>
     */
    private Integer heightCm;

    /**
     * 学校
     *
     * <p>
     * 用户就读或毕业的学校名称。
     * 可用于筛选和共同话题发现。
     * </p>
     */
    private String school;

    /**
     * 最高教育程度。
     *
     * <p>使用稳定的 {@link EducationLevel} 枚举而非自由文本，避免硬门禁因同义词或大小写
     * 差异产生不一致结果。未填写时保持 {@code null}，表示资料未知。</p>
     */
    private EducationLevel educationLevel;

    /**
     * 职业
     *
     * <p>
     * 用户当前从事的职业。
     * 用于展示和个人资料丰富度。
     * </p>
     */
    private String job;

    /**
     * 个人简介
     *
     * <p>
     * 用户撰写的自我介绍文字。
     * 用于展示给其他用户。
     * </p>
     *
     * <p><strong>长度限制</strong></p>
     * <p>建议控制在200字以内</p>
     */
    private String bio;

    /**
     * 自定义兴趣标签（JSON数组格式）。
     *
     * <p>P0-12修复(FE-2): 新增字段，用于持久化用户提交的自定义兴趣标签。
     * 以JSON字符串数组格式存储，如：["篮球","摄影","旅行"]。</p>
     *
     * @since 2.1.0
     */
    private String interests;

    /**
     * 社交用户标识
     *
     * <p>
     * 标识用户是否为付费社交用户。
     * <ul>
     *   <li>0 - 普通用户</li>
     *   <li>1 - 社交用户（已付费）</li>
     * </ul>
     * </p>
     *
     * @see #isSocialUser()
     */
    private Integer socialUser;

    /**
     * 成为社交用户时间
     *
     * <p>
     * 记录用户成为社交用户的时间戳。
     * 用于计算会员时长和续费提醒。
     * </p>
     *
     * @see #socialUser
     */
    private LocalDateTime socialUserAt;

    /**
     * 默认构造函数
     */
    public UserProfileEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建新的用户画像
     *
     * <p>
     * 为指定用户创建初始画像，设置默认值。
     * </p>
     *
     * <p><strong>默认设置</strong></p>
     * <ul>
     *   <li>socialUser = 0（非社交用户）</li>
     *   <li>deleted = 0（未删除）</li>
     *   <li>创建时间和更新时间自动设置</li>
     * </ul>
     *
     * @param userId 用户ID，不能为空
     * @return 新创建的用户画像实体
     * @throws IllegalArgumentException 如果userId为空
     */
    public static UserProfileEntity create(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setSocialUser(SocialUserType.NORMAL.getCode());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
        return entity;
    }

    /**
     * 领域行为：更新个人简介
     *
     * @param bio 新的个人简介
     */
    public void updateBio(String bio) {
        this.bio = bio;
        touch();
    }

    /**
     * 领域行为：更新交友目的
     *
     * @param datingGoal 新的交友目的
     */
    public void updateDatingGoal(String datingGoal) {
        this.datingGoal = datingGoal;
        touch();
    }

    /**
     * 领域行为：更新MBTI
     *
     * @param mbti 新的MBTI类型
     */
    public void updateMbti(String mbti) {
        this.mbti = mbti;
        touch();
    }

    /**
     * 领域行为：更新教育程度。
     *
     * @param educationLevel 新的教育程度；{@code null} 表示清空已填写的教育程度
     */
    public void updateEducationLevel(EducationLevel educationLevel) {
        this.educationLevel = educationLevel;
        touch();
    }

    /**
     * 领域行为：检查是否为社交用户
     *
     * @return true-是社交用户，false-普通用户
     */
    public boolean isSocialUser() {
        return socialUser != null && socialUser.equals(SocialUserType.SOCIAL.getCode());
    }

    /**
     * 领域行为：升级为社交用户
     *
     * <p>
     * 将用户标记为社交用户并记录升级时间。
     * </p>
     */
    public void upgradeToSocialUser() {
        this.socialUser = SocialUserType.SOCIAL.getCode();
        this.socialUserAt = LocalDateTime.now();
        touch();
    }

    /**
     * 领域行为：降级为普通用户
     *
     * <p>
     * 将用户降级为普通用户。
     * 通常在会员到期时调用。
     * </p>
     */
    public void downgradeToNormalUser() {
        this.socialUser = SocialUserType.NORMAL.getCode();
        touch();
    }

    /**
     * 社交用户类型枚举
     *
     * <p>定义用户的社交身份类型，用于控制功能权限。</p>
     */
    @Getter
    public enum SocialUserType {
        /** 普通用户 */
        NORMAL(0),
        /** 社交用户（已付费） */
        SOCIAL(1);

        private final Integer code;

        /**
         * 创建 {@code SocialUserType} 实例。
         *
         * @param code 枚举编码
         */
        SocialUserType(Integer code) {
            this.code = code;
        }

        /**
         * 根据枚举编码解析社交用户类型。
         *
         * @param code 社交用户类型编码
         * @return 匹配的社交用户类型；编码为空或未匹配时返回 {@code null}
         */
        public static SocialUserType fromCode(Integer code) {
            if (code == null) {
                return null;
            }
            for (SocialUserType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return null;
        }
    }
}
