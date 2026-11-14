package com.apex.scaffold.contract.dto.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一待办条目内部契约。
 *
 * <p>各业务域内部待办端点（{@code /internal/v1/workflow/todo/tasks}、
 * {@code /internal/v1/office/todo}）与 system 统一待办门面
 * （{@code GET /api/v2/todo}）之间的跨服务条目载体。契约字段名勿改，
 * 下游消费方按 {@code (来源, 类型, refId)} 自行路由到归属域页面。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalTodoItem {

    /** 条目类型，如 workflow_task / office_approval / office_message。 */
    private String itemType;

    /** 展示标题。 */
    private String title;

    /** 跳转链接，预留可空；前端按来源与类型自行路由。 */
    private String link;

    /** 发生时间。 */
    private LocalDateTime occurredAt;

    /** 条目状态，如 PENDING / UNREAD。 */
    private String status;

    /** 归属域内业务标识，供前端定位详情。 */
    private String refId;
}
