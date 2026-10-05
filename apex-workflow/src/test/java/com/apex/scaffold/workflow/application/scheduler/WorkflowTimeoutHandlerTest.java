package com.apex.scaffold.workflow.application.scheduler;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.repository.TaskRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("工作流超时处理器单元测试")
class WorkflowTimeoutHandlerTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private WorkflowService workflowService;

    @InjectMocks
    private WorkflowTimeoutHandler timeoutHandler;

    private Task escalateTask;
    private Task autoCompleteTask;
    private Task notifyTask;

    @BeforeEach
    void setUp() {
        escalateTask = new Task();
        escalateTask.setId(1000L);
        escalateTask.setProcessInstanceId(100L);
        escalateTask.setNodeCode("manager_approval");
        escalateTask.setAssigneeId(10002L);
        escalateTask.setStatus("PENDING");
        escalateTask.setTimeoutAt(LocalDateTime.now().minusHours(1));
        escalateTask.setTimeoutStrategy("ESCALATE");

        autoCompleteTask = new Task();
        autoCompleteTask.setId(1001L);
        autoCompleteTask.setProcessInstanceId(101L);
        autoCompleteTask.setNodeCode("dept_approval");
        autoCompleteTask.setAssigneeId(10003L);
        autoCompleteTask.setStatus("PENDING");
        autoCompleteTask.setTimeoutAt(LocalDateTime.now().minusHours(2));
        autoCompleteTask.setTimeoutStrategy("AUTO_COMPLETE");

        notifyTask = new Task();
        notifyTask.setId(1002L);
        notifyTask.setProcessInstanceId(102L);
        notifyTask.setNodeCode("finance_approval");
        notifyTask.setAssigneeId(10004L);
        notifyTask.setStatus("PENDING");
        notifyTask.setTimeoutAt(LocalDateTime.now().minusMinutes(30));
        notifyTask.setTimeoutStrategy("NOTIFY");
    }

    @Test
    @DisplayName("处理超时任务 - ESCALATE 策略")
    void testHandleTimeoutTasks_EscalateStrategy() {
        when(taskRepository.findPendingTasks()).thenReturn(Flux.just(escalateTask));
        when(workflowService.transferTask(anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(Mono.empty());

        timeoutHandler.handleTimeoutTasks();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(taskRepository, times(1)).findPendingTasks();
        verify(workflowService, times(1)).transferTask(eq(1000L), eq(1L), eq(10002L), eq("任务超时自动升级"));
    }

    @Test
    @DisplayName("处理超时任务 - AUTO_COMPLETE 策略")
    void testHandleTimeoutTasks_AutoCompleteStrategy() {
        when(taskRepository.findPendingTasks()).thenReturn(Flux.just(autoCompleteTask));
        when(workflowService.completeTask(anyLong(), anyLong(), anyString())).thenReturn(Mono.empty());

        timeoutHandler.handleTimeoutTasks();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(taskRepository, times(1)).findPendingTasks();
        verify(workflowService, times(1)).completeTask(eq(1001L), eq(10003L), eq("系统自动审批（超时）"));
    }

    @Test
    @DisplayName("处理超时任务 - NOTIFY 策略")
    void testHandleTimeoutTasks_NotifyStrategy() {
        when(taskRepository.findPendingTasks()).thenReturn(Flux.just(notifyTask));

        timeoutHandler.handleTimeoutTasks();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(taskRepository, times(1)).findPendingTasks();
        verify(workflowService, never()).transferTask(anyLong(), anyLong(), anyLong(), anyString());
        verify(workflowService, never()).completeTask(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("处理超时任务 - 混合策略")
    void testHandleTimeoutTasks_MixedStrategies() {
        when(taskRepository.findPendingTasks()).thenReturn(Flux.just(escalateTask, autoCompleteTask, notifyTask));
        when(workflowService.transferTask(anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(Mono.empty());
        when(workflowService.completeTask(anyLong(), anyLong(), anyString())).thenReturn(Mono.empty());

        timeoutHandler.handleTimeoutTasks();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(taskRepository, times(1)).findPendingTasks();
        verify(workflowService, times(1)).transferTask(eq(1000L), eq(1L), eq(10002L), eq("任务超时自动升级"));
        verify(workflowService, times(1)).completeTask(eq(1001L), eq(10003L), eq("系统自动审批（超时）"));
    }

    @Test
    @DisplayName("处理超时任务 - 无超时任务")
    void testHandleTimeoutTasks_NoTimeoutTasks() {
        when(taskRepository.findPendingTasks()).thenReturn(Flux.empty());

        timeoutHandler.handleTimeoutTasks();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(taskRepository, times(1)).findPendingTasks();
        verify(workflowService, never()).transferTask(anyLong(), anyLong(), anyLong(), anyString());
        verify(workflowService, never()).completeTask(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("处理超时任务 - 未超时的任务")
    void testHandleTimeoutTasks_NotYetTimeout() {
        Task futureTask = new Task();
        futureTask.setId(1003L);
        futureTask.setTimeoutAt(LocalDateTime.now().plusHours(1));
        futureTask.setTimeoutStrategy("ESCALATE");

        when(taskRepository.findPendingTasks()).thenReturn(Flux.just(futureTask));

        timeoutHandler.handleTimeoutTasks();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(taskRepository, times(1)).findPendingTasks();
        verify(workflowService, never()).transferTask(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("处理超时任务 - 单个任务处理失败")
    void testHandleTimeoutTasks_SingleTaskFailure() {
        when(taskRepository.findPendingTasks()).thenReturn(Flux.just(escalateTask, autoCompleteTask));
        when(workflowService.transferTask(anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("转办失败")));
        when(workflowService.completeTask(anyLong(), anyLong(), anyString())).thenReturn(Mono.empty());

        timeoutHandler.handleTimeoutTasks();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(taskRepository, times(1)).findPendingTasks();
        verify(workflowService, times(1)).transferTask(eq(1000L), eq(1L), eq(10002L), eq("任务超时自动升级"));
        verify(workflowService, times(1)).completeTask(eq(1001L), eq(10003L), eq("系统自动审批（超时）"));
    }

    @Test
    @DisplayName("处理超时任务 - 未知策略")
    void testHandleTimeoutTasks_UnknownStrategy() {
        Task unknownTask = new Task();
        unknownTask.setId(1003L);
        unknownTask.setTimeoutAt(LocalDateTime.now().minusHours(1));
        unknownTask.setTimeoutStrategy("UNKNOWN_STRATEGY");

        when(taskRepository.findPendingTasks()).thenReturn(Flux.just(unknownTask));

        timeoutHandler.handleTimeoutTasks();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(taskRepository, times(1)).findPendingTasks();
        verify(workflowService, never()).transferTask(anyLong(), anyLong(), anyLong(), anyString());
        verify(workflowService, never()).completeTask(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("处理超时任务 - null 策略默认为 ESCALATE")
    void testHandleTimeoutTasks_NullStrategyDefaultsToEscalate() {
        Task nullStrategyTask = new Task();
        nullStrategyTask.setId(1004L);
        nullStrategyTask.setAssigneeId(10005L);
        nullStrategyTask.setTimeoutAt(LocalDateTime.now().minusHours(1));
        nullStrategyTask.setTimeoutStrategy(null);

        when(taskRepository.findPendingTasks()).thenReturn(Flux.just(nullStrategyTask));
        when(workflowService.transferTask(anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(Mono.empty());

        timeoutHandler.handleTimeoutTasks();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(taskRepository, times(1)).findPendingTasks();
        verify(workflowService, times(1)).transferTask(eq(1004L), eq(1L), eq(10005L), eq("任务超时自动升级"));
    }
}
