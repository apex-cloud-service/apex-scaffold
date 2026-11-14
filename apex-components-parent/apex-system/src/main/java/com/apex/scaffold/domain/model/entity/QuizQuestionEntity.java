package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 答题题目领域实体
 *
 * <p>
 * 表示ELO答题功能中的一道题目，包含题目文本、选项和关联的吸引力维度。
 * 用户回答问题后，系统根据ELO算法更新该维度下的评分。
 * </p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>题目通过{@link #questionId}唯一标识</li>
 *   <li>每个题目关联一个吸引力维度，用于更新对应维度的ELO评分</li>
 *   <li>题目可以启用或停用，停用的题目不会出现在随机抽取中</li>
 *   <li>{@link #kFactor}控制ELO评分调整幅度，默认32</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class QuizQuestionEntity extends BaseEntity {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 题目唯一标识
     *
     * <p>
     * 题目的业务主键，用于关联答题记录。
     * </p>
     */
    @lombok.Setter
    private String questionId;

    /**
     * 题目文本
     *
     * <p>
     * 题目的显示文本内容。
     * </p>
     */
    @lombok.Setter
    private String questionText;

    /**
     * 选项JSON
     *
     * <p>
     * 题目选项的JSON字符串，包含选项文本和对应ELO分值。
     * </p>
     */
    @lombok.Setter
    private String optionsJson;

    /**
     * 关联的吸引力维度
     *
     * <p>
     * 此题目所属的吸引力维度，答题后将更新对应维度的ELO评分。
     * </p>
     */
    @lombok.Setter
    private String dimension;

    /**
     * ELO K值系数
     *
     * <p>
     * 控制ELO评分在该题目上的调整幅度，默认32。
     * </p>
     */
    private Integer kFactor = 32;

    /**
     * 是否启用
     *
     * <p>
     * 0-未启用，1-已启用。停用的题目不会被随机抽取。
     * </p>
     */
    @lombok.Setter
    private Integer enabled;

    /**
     * 默认构造函数
     */
    public QuizQuestionEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建题目
     *
     * @param questionId   题目唯一标识
     * @param questionText 题目文本
     * @param optionsJson  选项JSON
     * @param dimension    关联的吸引力维度
     * @return 新创建的题目实体
     */
    public static QuizQuestionEntity create(
            String questionId, String questionText, String optionsJson, String dimension) {
        QuizQuestionEntity entity = new QuizQuestionEntity();
        entity.questionId = questionId;
        entity.questionText = questionText;
        entity.optionsJson = optionsJson;
        entity.dimension = dimension;
        entity.kFactor = 32;
        entity.enabled = 1;
        entity.setTimestamps();
        return entity;
    }

    /**
     * 设置时间戳
     */
    private void setTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        this.setCreateTime(now);
        this.setUpdateTime(now);
        this.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
    }

    /**
     * 设置 K 因子值。
     *
     * <p>仅用于持久化和构造，不应在业务代码中直接调用。</p>
     *
     * @param value K 因子值
     */
    public void setkFactor(Integer value) {
        this.kFactor = value;
    }
}
