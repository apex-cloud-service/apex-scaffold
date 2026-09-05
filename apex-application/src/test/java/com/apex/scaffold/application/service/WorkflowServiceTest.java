package com.apex.scaffold.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.domain.model.workflow.ProcessInstance;
import com.apex.scaffold.domain.model.workflow.Task;
import com.apex.scaffold.domain.model.workflow.WorkflowDefinition;
import com.apex.scaffold.domain.repository.ProcessInstanceRepository;
import com.apex.scaffold.domain.repository.TaskRepository;
import com.apex.scaffold.domain.repository.WorkflowDefinitionRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("工作流服务单元测试")
class WorkflowServiceTest {

    @Mock
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private WorkflowService workflowService;

    private WorkflowDefinition testDefinition;
    private ProcessInstance testInstance;
    private Task testTask;

    @BeforeEach
    void setUp() {
        testDefinition = new WorkflowDefinition();
        testDefinition.setId(1L);
        testDefinition.setWorkflowCode("leave_approval");
        testDefinition.setWorkflowName("请假审批流程");
        testDefinition.setDagJson("{\"nodes\": [], \"edges\": []}");

        testInstance = new ProcessInstance();
        testInstance.setId(100L);
        testInstance.setWorkflowDefinitionId(1L);
        testInstance.setBusinessKey("LEAVE-2026-001");
        testInstance.setStatus("RUNNING");
        testInstance.setInitiatorId(10001L);
        testInstance.setContextJson("{\"days\": 3}");
        testInstance.setStartTime(LocalDateTime.now());
        testInstance.setSuspended(false);

        testTask = new Task();
        testTask.setId(1000L);
        testTask.setProcessInstanceId(100L);
        testTask.setNodeCode("manager_approval");
        testTask.setAssigneeId(10002L);
        testTask.setStatus("PENDING");
        testTask.setCreatedTime(LocalDateTime.now());
    }

    @Nested
    @DisplayName("流程定义管理测试")
    class WorkflowDefinitionTests {

        @Test
        @DisplayName("创建工作流定义成功")
        void testCreateWorkflowDefinition_Success() {
            when(workflowDefinitionRepository.insert(any(WorkflowDefinition.class)))
                    .thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.createWorkflowDefinition(testDefinition))
                    .expectNext(testDefinition)
                    .verifyComplete();

            verify(workflowDefinitionRepository, times(1)).insert(testDefinition);
        }

        @Test
        @DisplayName("根据编码查询工作流定义")
        void testGetWorkflowDefinitionByCode() {
            when(workflowDefinitionRepository.findByCode("leave_approval")).thenReturn(Mono.just(testDefinition));

            StepVerifier.create(workflowService.getWorkflowDefinitionByCode("leave_approval"))
                    .expectNext(testDefinition)
                    .verifyComplete();

            verify(workflowDefinitionRepository, times(1)).findByCode("leave_approval");
        }
    }

    @Nested
    @DisplayName("流程实例管理测试")
    class ProcessInstanceTests {

        @Test
        @DisplayName("发起流程实例成功")
        void testStartProcessInstance_Success() {
            when(workflowDefinitionRepository.findByCode("leave_approval")).thenReturn(Mono.just(testDefinition));
            when(processInstanceRepository.insert(any(ProcessInstance.class))).thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.startProcessInstance(
                            "leave_approval", "LEAVE-2026-001", 10001L, "{\"days\": 3}"))
                    .assertNext(instance -> {
                        assertNotNull(instance);
                        assertEquals("LEAVE-2026-001", instance.getBusinessKey());
                        assertEquals("RUNNING", instance.getStatus());
                        assertEquals(10001L, instance.getInitiatorId());
                        assertNotNull(instance.getStartTime());
                    })
                    .verifyComplete();

            verify(workflowDefinitionRepository, times(1)).findByCode("leave_approval");
            verify(processInstanceRepository, times(1)).insert(any(ProcessInstance.class));
        }

        @Test
        @DisplayName("查询流程实例")
        void testGetProcessInstance() {
            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));

            StepVerifier.create(workflowService.getProcessInstance(100L))
                    .expectNext(testInstance)
                    .verifyComplete();

            verify(processInstanceRepository, times(1)).findById(100L);
        }
    }

    @Nested
    @DisplayName("任务管理测试")
    class TaskManagementTests {

        @Test
        @DisplayName("查询用户待办任务")
        void testGetPendingTasks() {
            List<Task> tasks = Arrays.asList(testTask);
            when(taskRepository.findByAssignee(10002L)).thenReturn(Mono.just(tasks));

            StepVerifier.create(workflowService.getPendingTasks(10002L))
                    .expectNext(tasks)
                    .verifyComplete();

            verify(taskRepository, times(1)).findByAssignee(10002L);
        }

        @Test
        @DisplayName("完成任务成功")
        void testCompleteTask_Success() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));
            when(taskRepository.updateById(any(Task.class))).thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.completeTask(1000L, 10002L, "同意"))
                    .verifyComplete();

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository, times(1)).updateById(taskCaptor.capture());

            Task updatedTask = taskCaptor.getValue();
            assertEquals("COMPLETED", updatedTask.getStatus());
            assertNotNull(updatedTask.getCompletedTime());
        }

        @Test
        @DisplayName("驳回任务成功")
        void testRejectTask_Success() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));
            when(taskRepository.updateById(any(Task.class))).thenReturn(Mono.just(1));
            when(processInstanceRepository.findById(1000L)).thenReturn(Mono.just(testInstance));
            when(processInstanceRepository.updateById(any(ProcessInstance.class)))
                    .thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.rejectTask(1000L, 10002L, "不同意"))
                    .verifyComplete();

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository, times(1)).updateById(taskCaptor.capture());

            Task updatedTask = taskCaptor.getValue();
            assertEquals("REJECTED", updatedTask.getStatus());
            assertNotNull(updatedTask.getCompletedTime());
        }
    }

    @Nested
    @DisplayName("流程回退测试")
    class RollbackTests {

        @Test
        @DisplayName("回退到指定节点成功")
        void testRollbackToNode_Success() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));
            when(taskRepository.updateById(any(Task.class))).thenReturn(Mono.just(1));
            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));
            when(taskRepository.insert(any(Task.class))).thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.rollbackToNode(1000L, "dept_manager_approval", 10002L, "材料不完整"))
                    .verifyComplete();

            ArgumentCaptor<Task> updateCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository, times(1)).updateById(updateCaptor.capture());
            assertEquals("ROLLED_BACK", updateCaptor.getValue().getStatus());

            ArgumentCaptor<Task> insertCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository, times(1)).insert(insertCaptor.capture());
            Task newTask = insertCaptor.getValue();
            assertEquals("dept_manager_approval", newTask.getNodeCode());
            assertEquals("PENDING", newTask.getStatus());
        }
    }

    @Nested
    @DisplayName("任务转办测试")
    class TransferTests {

        @Test
        @DisplayName("转办任务成功")
        void testTransferTask_Success() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));
            when(taskRepository.updateById(any(Task.class))).thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.transferTask(1000L, 10003L, 10002L, "本周出差"))
                    .verifyComplete();

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository, times(1)).updateById(taskCaptor.capture());

            Task updatedTask = taskCaptor.getValue();
            assertEquals(10003L, updatedTask.getAssigneeId());
            assertEquals(10002L, updatedTask.getTransferredFrom());
        }

        @Test
        @DisplayName("转办非待办任务失败")
        void testTransferTask_NotPendingTask() {
            testTask.setStatus("COMPLETED");
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));

            StepVerifier.create(workflowService.transferTask(1000L, 10003L, 10002L, "test"))
                    .expectError(IllegalStateException.class)
                    .verify();
        }

        @Test
        @DisplayName("转办他人任务失败")
        void testTransferTask_NotOwnTask() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));

            StepVerifier.create(workflowService.transferTask(1000L, 10003L, 99999L, "test"))
                    .expectError(IllegalStateException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("任务委派测试")
    class DelegateTests {

        @Test
        @DisplayName("委派任务成功")
        void testDelegateTask_Success() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));
            when(taskRepository.insert(any(Task.class))).thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.delegateTask(1000L, 10004L, 10002L, "请先初审"))
                    .verifyComplete();

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository, times(1)).insert(taskCaptor.capture());

            Task delegatedTask = taskCaptor.getValue();
            assertEquals("DELEGATED", delegatedTask.getStatus());
            assertEquals(10004L, delegatedTask.getAssigneeId());
            assertEquals(10002L, delegatedTask.getTransferredFrom());
            assertEquals(testTask.getProcessInstanceId(), delegatedTask.getProcessInstanceId());
        }

        @Test
        @DisplayName("委派非待办任务失败")
        void testDelegateTask_NotPendingTask() {
            testTask.setStatus("COMPLETED");
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));

            StepVerifier.create(workflowService.delegateTask(1000L, 10004L, 10002L, "test"))
                    .expectError(IllegalStateException.class)
                    .verify();
        }

        @Test
        @DisplayName("委派他人任务失败")
        void testDelegateTask_NotOwnTask() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));

            StepVerifier.create(workflowService.delegateTask(1000L, 10004L, 99999L, "test"))
                    .expectError(IllegalStateException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("流程挂起/恢复测试")
    class SuspendResumeTests {

        @Test
        @DisplayName("挂起流程实例成功")
        void testSuspendInstance_Success() {
            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));
            when(processInstanceRepository.updateById(any(ProcessInstance.class)))
                    .thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.suspendInstance(100L, 10001L, "数据异常"))
                    .verifyComplete();

            ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
            verify(processInstanceRepository, times(1)).updateById(instanceCaptor.capture());

            ProcessInstance updatedInstance = instanceCaptor.getValue();
            assertTrue(updatedInstance.getSuspended());
            assertEquals("SUSPENDED", updatedInstance.getStatus());
        }

        @Test
        @DisplayName("挂起已完成流程失败")
        void testSuspendInstance_AlreadyCompleted() {
            testInstance.setStatus("COMPLETED");
            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));

            StepVerifier.create(workflowService.suspendInstance(100L, 10001L, "test"))
                    .expectError(IllegalStateException.class)
                    .verify();
        }

        @Test
        @DisplayName("挂起已挂起流程失败")
        void testSuspendInstance_AlreadySuspended() {
            testInstance.setStatus("SUSPENDED");
            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));

            StepVerifier.create(workflowService.suspendInstance(100L, 10001L, "test"))
                    .expectError(IllegalStateException.class)
                    .verify();
        }

        @Test
        @DisplayName("恢复流程实例成功")
        void testResumeInstance_Success() {
            testInstance.setStatus("SUSPENDED");
            testInstance.setSuspended(true);
            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));
            when(processInstanceRepository.updateById(any(ProcessInstance.class)))
                    .thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.resumeInstance(100L, 10001L, "数据已修复"))
                    .verifyComplete();

            ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
            verify(processInstanceRepository, times(1)).updateById(instanceCaptor.capture());

            ProcessInstance updatedInstance = instanceCaptor.getValue();
            assertFalse(updatedInstance.getSuspended());
            assertEquals("RUNNING", updatedInstance.getStatus());
        }

        @Test
        @DisplayName("恢复非挂起流程失败")
        void testResumeInstance_NotSuspended() {
            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));

            StepVerifier.create(workflowService.resumeInstance(100L, 10001L, "test"))
                    .expectError(IllegalStateException.class)
                    .verify();
        }
    }
}
