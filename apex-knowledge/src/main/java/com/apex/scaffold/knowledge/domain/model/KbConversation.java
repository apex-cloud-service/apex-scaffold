package com.apex.scaffold.knowledge.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识问答会话领域模型。
 *
 * <p>会话仅承载归属（用户 + 租户）与展示标题；上下文裁剪与引用均发生在消息层，
 * 会话不缓存检索结果。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbConversation {

    /** 会话 ID（雪花 ID）。 */
    private Long id;

    /** 会话标题（默认取首条提问截断）。 */
    private String title;

    /** 归属用户 ID。 */
    private Long userId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
