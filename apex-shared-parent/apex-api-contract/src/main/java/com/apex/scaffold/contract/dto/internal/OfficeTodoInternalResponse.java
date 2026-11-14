package com.apex.scaffold.contract.dto.internal;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * office 统一待办内部响应。
 *
 * <p>{@code GET /internal/v1/office/todo} 的负载：单端点同时返回当前用户
 * 待审批流程与未读站内信两组条目，供 system 统一待办门面消费。契约字段名勿改。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficeTodoInternalResponse {

    /** 待审批条目（已按 limit 截断）。 */
    private List<InternalTodoItem> approvals;

    /** 未读站内信条目（已按 limit 截断）。 */
    private List<InternalTodoItem> messages;

    /** 待审批总数（截断前的真实数量）。 */
    private Long approvalTotal;

    /** 未读站内信总数。 */
    private Long unreadTotal;
}
