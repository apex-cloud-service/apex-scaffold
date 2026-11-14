package com.apex.scaffold.knowledge.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 知识问答消息持久化对象，对应表 {@code kb_message}。
 *
 * <p>{@code citations} 为引用溯源 JSON 数组（回答消息专用），以字符串存储，
 * 结构见领域模型 {@code KbMessage.Citation}。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("kb_message")
public class KbMessagePO extends BasePO {

    private static final long serialVersionUID = 1L;

    /** 所属会话 ID。 */
    private Long conversationId;

    /** 消息角色：USER/ASSISTANT。 */
    private String role;

    /** 消息正文。 */
    private String content;

    /** 引用溯源 JSON（可空）。 */
    private String citations;

    /** 租户 ID。 */
    private String tenantId;
}
