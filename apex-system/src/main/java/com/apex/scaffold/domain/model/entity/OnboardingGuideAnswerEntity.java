package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 引导页答题记录实体类
 *
 * <p>存储用户在引导页回答问题的记录
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OnboardingGuideAnswerEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 场景标识 */
    private String scene;

    /** 问题ID */
    private String questionId;

    /** 选中的选项ID */
    private String selectedOptionId;
}
