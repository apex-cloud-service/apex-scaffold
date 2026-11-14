package com.apex.scaffold.knowledge.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 知识问答会话持久化对象，对应表 {@code kb_conversation}。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("kb_conversation")
public class KbConversationPO extends BasePO {

    private static final long serialVersionUID = 1L;

    /** 会话标题。 */
    private String title;

    /** 归属用户 ID。 */
    private Long userId;

    /** 租户 ID。 */
    private String tenantId;
}
