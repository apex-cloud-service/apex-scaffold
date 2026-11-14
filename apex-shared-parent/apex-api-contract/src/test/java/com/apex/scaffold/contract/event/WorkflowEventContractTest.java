package com.apex.scaffold.contract.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 跨服务工作流事件契约测试.
 *
 * <p>校验 {@code apex-workflow-events} 主题下三类跨服务事件（任务分配 / 任务完成 / 流程实例启动）
 * 的业务负载构造、事件源解析与继承自 {@link AbstractDomainEvent} 的事件信封能力，
 * 保障发布方与订阅方之间的负载契约不漂移。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("跨服务工作流事件契约测试")
class WorkflowEventContractTest {

    @Nested
    @DisplayName("TaskAssignedEvent 任务分配事件")
    class TaskAssignedEventTest {

        @Test
        @DisplayName("构造后业务负载可通过 getter 读取")
        void exposesBusinessPayload() {
            TaskAssignedEvent event = new TaskAssignedEvent(1001L, 2002L, "approve_node", 3003L, 4004L);
            assertThat(event.getTaskId()).isEqualTo(1001L);
            assertThat(event.getProcessInstanceId()).isEqualTo(2002L);
            assertThat(event.getNodeCode()).isEqualTo("approve_node");
            assertThat(event.getAssigneeId()).isEqualTo(3003L);
            assertThat(event.getAssignedBy()).isEqualTo(4004L);
        }

        @Test
        @DisplayName("getSourceId 返回任务 ID 字符串，任务 ID 为空时返回 null")
        void resolvesSourceId() {
            assertThat(new TaskAssignedEvent(1001L, 2002L, "approve_node", 3003L, null).getSourceId())
                    .isEqualTo("1001");
            assertThat(new TaskAssignedEvent(null, 2002L, "approve_node", 3003L, null).getSourceId())
                    .isNull();
        }

        @Test
        @DisplayName("继承事件信封：事件名/时间戳/租户上下文/已处理标记")
        void inheritsEventEnvelope() {
            TaskAssignedEvent event = new TaskAssignedEvent(1001L, 2002L, "approve_node", 3003L, null);
            assertThat(event).isInstanceOf(AbstractDomainEvent.class);
            assertThat(event.getEventName()).isEqualTo("TaskAssignedEvent");
            assertThat(event.getEventId()).isNotBlank();
            assertThat(event.getCreatedAt()).isNotNull();
            event.attachTenantContext("tenant-1");
            assertThat(event.getTenantId()).isEqualTo("tenant-1");
            event.markAsProcessed();
            assertThat(event.isProcessed()).isTrue();
        }
    }

    @Nested
    @DisplayName("TaskCompletedEvent 任务完成事件")
    class TaskCompletedEventTest {

        @Test
        @DisplayName("构造后业务负载可通过 getter 读取")
        void exposesBusinessPayload() {
            LocalDateTime completedAt = LocalDateTime.of(2024, 5, 1, 10, 0);
            TaskCompletedEvent event =
                    new TaskCompletedEvent(1001L, 2002L, 3003L, TaskCompletedEvent.ACTION_APPROVE, "同意", completedAt);
            assertThat(event.getTaskId()).isEqualTo(1001L);
            assertThat(event.getProcessInstanceId()).isEqualTo(2002L);
            assertThat(event.getApproverId()).isEqualTo(3003L);
            assertThat(event.getAction()).isEqualTo(TaskCompletedEvent.ACTION_APPROVE);
            assertThat(event.getComment()).isEqualTo("同意");
            assertThat(event.getCompletedAt()).isEqualTo(completedAt);
        }

        @Test
        @DisplayName("getSourceId 返回任务 ID 字符串，任务 ID 为空时返回 null")
        void resolvesSourceId() {
            assertThat(new TaskCompletedEvent(1001L, 2002L, 3003L, TaskCompletedEvent.ACTION_REJECT, null, null)
                            .getSourceId())
                    .isEqualTo("1001");
            assertThat(new TaskCompletedEvent(null, 2002L, 3003L, TaskCompletedEvent.ACTION_REJECT, null, null)
                            .getSourceId())
                    .isNull();
        }
    }

    @Nested
    @DisplayName("WorkflowInstanceStartedEvent 流程实例启动事件")
    class WorkflowInstanceStartedEventTest {

        @Test
        @DisplayName("构造后业务负载可通过 getter 读取")
        void exposesBusinessPayload() {
            LocalDateTime startTime = LocalDateTime.of(2024, 5, 1, 9, 30);
            WorkflowInstanceStartedEvent event =
                    new WorkflowInstanceStartedEvent(2002L, "leave_approval", "BIZ-001", 3003L, startTime);
            assertThat(event.getProcessInstanceId()).isEqualTo(2002L);
            assertThat(event.getWorkflowCode()).isEqualTo("leave_approval");
            assertThat(event.getBusinessKey()).isEqualTo("BIZ-001");
            assertThat(event.getInitiatorId()).isEqualTo(3003L);
            assertThat(event.getStartTime()).isEqualTo(startTime);
        }

        @Test
        @DisplayName("getSourceId 返回流程实例 ID 字符串，实例 ID 为空时返回 null")
        void resolvesSourceId() {
            assertThat(new WorkflowInstanceStartedEvent(2002L, "leave_approval", "BIZ-001", 3003L, null).getSourceId())
                    .isEqualTo("2002");
            assertThat(new WorkflowInstanceStartedEvent(null, "leave_approval", "BIZ-001", 3003L, null).getSourceId())
                    .isNull();
        }
    }
}
