package com.apex.scaffold.workflow.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.workflow.domain.model.ProcessInstance;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinition;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinitionStatus;
import com.apex.scaffold.workflow.domain.repository.ProcessInstanceRepository;
import com.apex.scaffold.workflow.domain.repository.TaskRepository;
import com.apex.scaffold.workflow.domain.repository.WorkflowDefinitionRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("工作流服务单元测试")
class WorkflowServiceTest {

    /** 首期可执行的单链流程：开始 → 经理审批 → 结束。 */
    private static final String VALID_DAG_JSON = "{\"nodes\":["
            + "{\"nodeId\":\"start\",\"nodeType\":\"START\"},"
            + "{\"nodeId\":\"manager_approval\",\"nodeType\":\"APPROVAL\",\"nodeName\":\"经理审批\","
            + "\"approverType\":\"INITIATOR\"},"
            + "{\"nodeId\":\"end\",\"nodeType\":\"END\"}],"
            + "\"edges\":["
            + "{\"edgeId\":\"e1\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"manager_approval\"},"
            + "{\"edgeId\":\"e2\",\"sourceNodeId\":\"manager_approval\",\"targetNodeId\":\"end\"}]}";

    @Mock
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    @Mock
    private TaskRepository taskRepository;

    private WorkflowService workflowService;

    private WorkflowDefinition testDefinition;
    private ProcessInstance testInstance;
    private Task testTask;

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowService(
                workflowDefinitionRepository,
                processInstanceRepository,
                taskRepository,
                (tenant, operation) -> operation.accept(
                        new com.apex.scaffold.workflow.domain.repository.WorkflowTaskTransaction.Operations() {
                            public Task findTask(Long id) {
                                return taskRepository.findById(id).block();
                            }

                            public int updateTask(Task task) {
                                return taskRepository.updateById(task).block();
                            }

                            public int updateTimeoutTask(Task task, Task expected) {
                                return taskRepository.updateById(task).block();
                            }

                            public int insertTask(Task task) {
                                return taskRepository.insert(task).block();
                            }

                            public WorkflowDefinition findDefinition(Long id) {
                                return workflowDefinitionRepository.findById(id).block();
                            }

                            public ProcessInstance findInstance(Long id) {
                                return processInstanceRepository.findById(id).block();
                            }

                            public int insertInstance(ProcessInstance instance) {
                                return processInstanceRepository
                                        .insert(instance)
                                        .block();
                            }

                            public int updateInstance(ProcessInstance instance) {
                                return processInstanceRepository
                                        .updateById(instance)
                                        .block();
                            }

                            public void appendEvent(com.apex.scaffold.common.event.AbstractDomainEvent event) {
                                // 单元测试不涉及持久化 Outbox；事件构造与落箱由集成测试覆盖。
                            }
                        }));
        testDefinition = new WorkflowDefinition();
        testDefinition.setId(1L);
        testDefinition.setWorkflowCode("leave_approval");
        testDefinition.setWorkflowName("请假审批流程");
        testDefinition.setVersion(1);
        testDefinition.setStatus(WorkflowDefinitionStatus.PUBLISHED);
        testDefinition.setEnabled(1);
        testDefinition.setDagJson(VALID_DAG_JSON);

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

    private <T> reactor.test.StepVerifier.FirstStep<T> verifyWithTenant(Mono<T> result) {
        return StepVerifier.create(result.contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("1")));
    }

    static Stream<Arguments> invalidTaskOperations() {
        return Stream.of("完成", "驳回", "回退", "转办", "委派")
                .flatMap(operation -> Stream.of(
                        Arguments.of(operation, "不存在", "任务不存在"),
                        Arguments.of(operation, "空操作人", "只能" + operation + "自己的任务"),
                        Arguments.of(operation, "空办理人", "只能" + operation + "自己的任务"),
                        Arguments.of(operation, "双方为空", "只能" + operation + "自己的任务"),
                        Arguments.of(operation, "他人任务", "只能" + operation + "自己的任务"),
                        Arguments.of(operation, "COMPLETED", "只能" + operation + "待办任务"),
                        Arguments.of(operation, "REJECTED", "只能" + operation + "待办任务"),
                        Arguments.of(operation, "ROLLED_BACK", "只能" + operation + "待办任务"),
                        Arguments.of(operation, "DELEGATED", "只能" + operation + "待办任务"),
                        Arguments.of(operation, "空状态", "只能" + operation + "待办任务")));
    }

    @ParameterizedTest(name = "{0}拒绝{1}且不写库")
    @MethodSource("invalidTaskOperations")
    void rejectsInvalidTaskWithoutWriting(String operation, String scenario, String message) {
        Long operatorId = 10002L;
        switch (scenario) {
            case "空操作人" -> operatorId = null;
            case "空办理人" -> testTask.setAssigneeId(null);
            case "双方为空" -> {
                operatorId = null;
                testTask.setAssigneeId(null);
            }
            case "他人任务" -> operatorId = 99999L;
            case "空状态" -> testTask.setStatus(null);
            case "不存在" -> {}
            default -> testTask.setStatus(scenario);
        }
        String originalStatus = testTask.getStatus();
        Long originalAssignee = testTask.getAssigneeId();
        when(taskRepository.findById(1000L)).thenReturn("不存在".equals(scenario) ? Mono.empty() : Mono.just(testTask));

        Mono<Void> result =
                switch (operation) {
                    case "完成" -> workflowService.completeTask(1000L, operatorId, "测试");
                    case "驳回" -> workflowService.rejectTask(1000L, operatorId, "测试");
                    case "回退" -> workflowService.rollbackToNode(1000L, "previous", operatorId, "测试");
                    case "转办" -> workflowService.transferTask(1000L, 10003L, operatorId, "测试");
                    case "委派" -> workflowService.delegateTask(1000L, 10004L, operatorId, "测试");
                    default -> throw new IllegalArgumentException(operation);
                };
        verifyWithTenant(result)
                .expectErrorMatches(
                        error -> error instanceof IllegalStateException && message.equals(error.getMessage()))
                .verify();
        verify(taskRepository).findById(1000L);
        verifyNoMoreInteractions(taskRepository);
        verifyNoInteractions(processInstanceRepository, workflowDefinitionRepository);
        assertEquals(originalStatus, testTask.getStatus());
        assertEquals(originalAssignee, testTask.getAssigneeId());
        assertNull(testTask.getCompletedTime());
        assertNull(testTask.getTransferredFrom());
    }

    @Test
    void transactionIsDeferredAndUsesTrustedTenantOnJdbcScheduler() {
        var port = mock(com.apex.scaffold.workflow.domain.repository.WorkflowTaskTransaction.class);
        var controlled =
                new WorkflowService(workflowDefinitionRepository, processInstanceRepository, taskRepository, port);
        doAnswer(invocation -> {
                    assertEquals("tenant-A", invocation.getArgument(0));
                    assertTrue(Thread.currentThread().getName().startsWith("apex.jdbc-"));
                    return null;
                })
                .when(port)
                .execute(eq("tenant-A"), any());
        Mono<Void> operation = controlled.rejectTask(1000L, 10002L, "test");
        verifyNoInteractions(port);
        StepVerifier.create(operation).expectErrorMessage("工作流仓储缺少有效租户上下文").verify();
        verifyNoInteractions(port);
        StepVerifier.create(
                        operation.contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("tenant-A")))
                .verifyComplete();
        verify(port).execute(eq("tenant-A"), any());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"reject", "rollback", "delegate"})
    void missingInstanceFailsAfterTaskCas(String operation) {
        when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));
        when(taskRepository.updateById(testTask)).thenReturn(Mono.just(1));
        when(processInstanceRepository.findById(100L)).thenReturn(Mono.empty());
        verifyWithTenant(multiStep(operation)).expectErrorMessage("流程实例不存在").verify();
        verify(taskRepository).updateById(testTask);
        verify(taskRepository, never()).insert(any());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"reject", "rollback", "delegate"})
    void lostCasStopsFollowUp(String operation) {
        when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));
        when(taskRepository.updateById(testTask)).thenReturn(Mono.just(0));
        verifyWithTenant(multiStep(operation)).expectErrorMessage("任务已被其他操作修改").verify();
        verifyNoInteractions(processInstanceRepository);
        verify(taskRepository, never()).insert(any());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"reject", "rollback", "delegate"})
    void zeroFollowUpWriteFails(String operation) {
        when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));
        when(taskRepository.updateById(testTask)).thenReturn(Mono.just(1));
        when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));
        if (operation.equals("reject")) {
            when(processInstanceRepository.updateById(testInstance)).thenReturn(Mono.just(0));
        } else {
            when(taskRepository.insert(any())).thenReturn(Mono.just(0));
        }
        verifyWithTenant(multiStep(operation))
                .expectErrorMessage(operation.equals("reject") ? "流程实例更新失败" : "任务创建失败")
                .verify();
    }

    private Mono<Void> multiStep(String operation) {
        return switch (operation) {
            case "reject" -> workflowService.rejectTask(1000L, 10002L, "test");
            case "rollback" -> workflowService.rollbackToNode(1000L, "previous", 10002L, "test");
            default -> workflowService.delegateTask(1000L, 10004L, 10002L, "test");
        };
    }

    @Nested
    @DisplayName("流程定义管理测试")
    class WorkflowDefinitionTests {

        @Test
        @DisplayName("创建首个草稿版本")
        void testCreateDraft_StartsAtVersionOne() {
            WorkflowDefinition payload = new WorkflowDefinition();
            payload.setWorkflowCode("leave_approval");
            payload.setWorkflowName("请假审批流程");
            payload.setDagJson(VALID_DAG_JSON);
            when(workflowDefinitionRepository.findVersionsByCode("leave_approval"))
                    .thenReturn(Mono.just(List.of()));
            when(workflowDefinitionRepository.insert(any(WorkflowDefinition.class)))
                    .thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.createDraft(payload, 10000L))
                    .assertNext(draft -> {
                        assertEquals(1, draft.getVersion());
                        assertEquals(WorkflowDefinitionStatus.DRAFT, draft.getStatus());
                        assertEquals(0, draft.getEnabled());
                        assertEquals(10000L, draft.getCreateBy());
                        assertNotNull(draft.getCreateTime());
                    })
                    .verifyComplete();

            verify(workflowDefinitionRepository, times(1)).insert(any(WorkflowDefinition.class));
            verifyNoMoreInteractions(workflowDefinitionRepository);
        }

        @Test
        @DisplayName("已有版本时草稿版本号递增")
        void testCreateDraft_IncrementsVersion() {
            WorkflowDefinition existing = new WorkflowDefinition();
            existing.setVersion(2);
            WorkflowDefinition payload = new WorkflowDefinition();
            payload.setWorkflowCode("leave_approval");
            payload.setWorkflowName("请假审批流程");
            payload.setDagJson(VALID_DAG_JSON);
            when(workflowDefinitionRepository.findVersionsByCode("leave_approval"))
                    .thenReturn(Mono.just(List.of(existing)));
            when(workflowDefinitionRepository.insert(any(WorkflowDefinition.class)))
                    .thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.createDraft(payload, 10000L))
                    .assertNext(draft -> assertEquals(3, draft.getVersion()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("发布草稿后内容不可变")
        void testPublishDefinition_Success() {
            WorkflowDefinition draft = new WorkflowDefinition();
            draft.setId(10L);
            draft.setWorkflowCode("leave_approval");
            draft.setDagJson(VALID_DAG_JSON);
            draft.setVersion(1);
            draft.setStatus(WorkflowDefinitionStatus.DRAFT);
            draft.setEnabled(0);
            when(workflowDefinitionRepository.findById(10L)).thenReturn(Mono.just(draft));
            when(workflowDefinitionRepository.updateById(draft)).thenReturn(Mono.just(1));

            StepVerifier.create(workflowService.publishDefinition(10L, 10000L))
                    .assertNext(published -> {
                        assertEquals(WorkflowDefinitionStatus.PUBLISHED, published.getStatus());
                        assertEquals(1, published.getEnabled());
                        assertEquals(10000L, published.getPublishedBy());
                        assertNotNull(published.getPublishedAt());
                    })
                    .verifyComplete();

            verify(workflowDefinitionRepository, times(1)).updateById(draft);
        }

        @Test
        @DisplayName("非草稿不能再次发布")
        void testPublishDefinition_RejectsNonDraft() {
            when(workflowDefinitionRepository.findById(1L)).thenReturn(Mono.just(testDefinition));

            StepVerifier.create(workflowService.publishDefinition(1L, 10000L))
                    .expectErrorMatches(
                            error -> error instanceof BusinessException && "只有草稿状态的流程定义可以发布".equals(error.getMessage()))
                    .verify();

            verify(workflowDefinitionRepository, never()).updateById(any(WorkflowDefinition.class));
        }

        @Test
        @DisplayName("发布时拒绝非法 DAG")
        void testPublishDefinition_RejectsInvalidGraph() {
            WorkflowDefinition draft = new WorkflowDefinition();
            draft.setId(12L);
            draft.setStatus(WorkflowDefinitionStatus.DRAFT);
            draft.setEnabled(0);
            draft.setDagJson("{\"nodes\":[{\"nodeId\":\"start\",\"nodeType\":\"START\"}]}");
            when(workflowDefinitionRepository.findById(12L)).thenReturn(Mono.just(draft));

            StepVerifier.create(workflowService.publishDefinition(12L, 10000L))
                    .expectErrorMatches(error -> error instanceof BusinessException
                            && error.getMessage().startsWith("流程定义不合法"))
                    .verify();

            verify(workflowDefinitionRepository, never()).updateById(any(WorkflowDefinition.class));
            assertEquals(WorkflowDefinitionStatus.DRAFT, draft.getStatus());
        }

        @Test
        @DisplayName("其他租户的定义不可见，发布被拒绝")
        void testPublishDefinition_RejectsInvisibleDefinition() {
            when(workflowDefinitionRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(workflowService.publishDefinition(99L, 10000L))
                    .expectErrorMatches(
                            error -> error instanceof BusinessException && "流程定义不存在".equals(error.getMessage()))
                    .verify();

            verify(workflowDefinitionRepository, never()).updateById(any(WorkflowDefinition.class));
        }

        @Test
        @DisplayName("已发布定义不能编辑")
        void testUpdateDraft_RejectsPublished() {
            when(workflowDefinitionRepository.findById(1L)).thenReturn(Mono.just(testDefinition));

            StepVerifier.create(workflowService.updateDraft(1L, new WorkflowDefinition(), 10000L))
                    .expectErrorMatches(
                            error -> error instanceof BusinessException && "只有草稿状态的流程定义可以编辑".equals(error.getMessage()))
                    .verify();

            verify(workflowDefinitionRepository, never()).updateById(any(WorkflowDefinition.class));
        }

        @Test
        @DisplayName("草稿不能启用或停用")
        void testEnableAndDisable_RejectDraft() {
            WorkflowDefinition draft = new WorkflowDefinition();
            draft.setId(11L);
            draft.setStatus(WorkflowDefinitionStatus.DRAFT);
            when(workflowDefinitionRepository.findById(11L)).thenReturn(Mono.just(draft));

            StepVerifier.create(workflowService.enableDefinition(11L, 10000L))
                    .expectErrorMatches(
                            error -> error instanceof BusinessException && "流程定义尚未发布，不能启用".equals(error.getMessage()))
                    .verify();
            StepVerifier.create(workflowService.disableDefinition(11L, 10000L))
                    .expectErrorMatches(
                            error -> error instanceof BusinessException && "流程定义尚未发布，不能停用".equals(error.getMessage()))
                    .verify();

            verify(workflowDefinitionRepository, never()).updateById(any(WorkflowDefinition.class));
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
            when(taskRepository.insert(any(Task.class))).thenReturn(Mono.just(1));

            verifyWithTenant(workflowService.startProcessInstance(
                            "leave_approval", "LEAVE-2026-001", 10001L, "{\"days\": 3}"))
                    .assertNext(instance -> {
                        assertNotNull(instance);
                        assertEquals("LEAVE-2026-001", instance.getBusinessKey());
                        assertEquals("RUNNING", instance.getStatus());
                        assertEquals(10001L, instance.getInitiatorId());
                        assertEquals("manager_approval", instance.getCurrentNodeKey());
                        assertNotNull(instance.getStartTime());
                    })
                    .verifyComplete();

            verify(workflowDefinitionRepository, times(1)).findByCode("leave_approval");
            verify(processInstanceRepository, times(1)).insert(any(ProcessInstance.class));
            verify(taskRepository, times(1)).insert(any(Task.class));
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
            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));
            when(workflowDefinitionRepository.findById(1L)).thenReturn(Mono.just(testDefinition));
            when(processInstanceRepository.updateById(any(ProcessInstance.class)))
                    .thenReturn(Mono.just(1));

            verifyWithTenant(workflowService.completeTask(1000L, 10002L, "同意")).verifyComplete();

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository, times(1)).updateById(taskCaptor.capture());

            Task updatedTask = taskCaptor.getValue();
            assertEquals("COMPLETED", updatedTask.getStatus());
            assertNotNull(updatedTask.getCompletedTime());
            assertEquals("COMPLETED", testInstance.getStatus());
            assertNotNull(testInstance.getEndTime());
        }

        @Test
        @DisplayName("完成任务按实例固定的旧版本定义推进")
        void testCompleteTask_UsesPinnedDefinitionVersion() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));
            when(taskRepository.updateById(any(Task.class))).thenReturn(Mono.just(1));
            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));
            when(workflowDefinitionRepository.findById(1L)).thenReturn(Mono.just(testDefinition));
            when(processInstanceRepository.updateById(any(ProcessInstance.class)))
                    .thenReturn(Mono.just(1));

            verifyWithTenant(workflowService.completeTask(1000L, 10002L, "同意")).verifyComplete();

            verify(workflowDefinitionRepository, times(1)).findById(1L);
            verify(workflowDefinitionRepository, never()).findByCode(anyString());
            assertEquals("COMPLETED", testInstance.getStatus());
        }

        @Test
        @DisplayName("驳回任务成功")
        void testRejectTask_Success() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));
            when(taskRepository.updateById(any(Task.class))).thenReturn(Mono.just(1));
            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));
            when(processInstanceRepository.updateById(any(ProcessInstance.class)))
                    .thenReturn(Mono.just(1));

            verifyWithTenant(workflowService.rejectTask(1000L, 10002L, "不同意")).verifyComplete();

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository, times(1)).updateById(taskCaptor.capture());

            Task updatedTask = taskCaptor.getValue();
            assertEquals("REJECTED", updatedTask.getStatus());
            assertNotNull(updatedTask.getCompletedTime());
            verify(processInstanceRepository).findById(100L);
            verify(processInstanceRepository, never()).findById(1000L);
            verify(processInstanceRepository).updateById(testInstance);
            assertEquals("REJECTED", testInstance.getStatus());
            assertNotNull(testInstance.getEndTime());
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

            verifyWithTenant(workflowService.rollbackToNode(1000L, "dept_manager_approval", 10002L, "材料不完整"))
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

            StepVerifier.create(workflowService
                            .transferTask(1000L, 10003L, 10002L, "本周出差")
                            .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("1")))
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

            StepVerifier.create(workflowService
                            .transferTask(1000L, 10003L, 10002L, "test")
                            .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("1")))
                    .expectError(IllegalStateException.class)
                    .verify();
        }

        @Test
        @DisplayName("转办他人任务失败")
        void testTransferTask_NotOwnTask() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));

            StepVerifier.create(workflowService
                            .transferTask(1000L, 10003L, 99999L, "test")
                            .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("1")))
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
            when(taskRepository.updateById(testTask)).thenReturn(Mono.just(1));
            when(taskRepository.insert(any(Task.class))).thenReturn(Mono.just(1));

            when(processInstanceRepository.findById(100L)).thenReturn(Mono.just(testInstance));
            verifyWithTenant(workflowService.delegateTask(1000L, 10004L, 10002L, "请先初审"))
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

            verifyWithTenant(workflowService.delegateTask(1000L, 10004L, 10002L, "test"))
                    .expectError(IllegalStateException.class)
                    .verify();
        }

        @Test
        @DisplayName("委派他人任务失败")
        void testDelegateTask_NotOwnTask() {
            when(taskRepository.findById(1000L)).thenReturn(Mono.just(testTask));

            verifyWithTenant(workflowService.delegateTask(1000L, 10004L, 99999L, "test"))
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
