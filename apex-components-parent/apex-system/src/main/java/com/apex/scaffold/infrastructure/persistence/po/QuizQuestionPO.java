package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 答题题目持久化对象。
 *
 * <p>映射数据库表 biz_quiz_question，记录答题功能的题目内容、
 * 选项及维度信息。
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
@TableName("biz_quiz_question")
public class QuizQuestionPO extends BasePO {

    /** 题目标识。 */
    @TableField("question_id")
    private String questionId;

    /** 题目内容。 */
    @TableField("question_text")
    private String questionText;

    /** 选项 JSON。 */
    @TableField("options_json")
    private String optionsJson;

    /** 维度。 */
    @TableField("dimension")
    private String dimension;

    /** K 系数。 */
    @TableField("k_factor")
    private Integer kFactor;

    /** 是否启用（0/1）。 */
    @TableField("enabled")
    private Integer enabled;
}
