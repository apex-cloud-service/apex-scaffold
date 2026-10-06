package com.apex.scaffold.workflow.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.workflow.application.dto.WorkflowDefinitionDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowInstanceDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowInstanceDetailDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowTaskDTO;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.ProcessInstancePO;
import com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO;
import com.apex.scaffold.workflow.infrastructure.persistence.po.WorkflowDefinitionPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link WorkflowQueryService} 单元测试
 *
 * <p>测试工作流查询服务的核心功能：流程实例查询、详情查询、任务查询
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowQueryService 单元测试")
class WorkflowQueryServiceTest {

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private WorkflowDefinitionMapper workflowDefinitionMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WorkflowQueryService queryService;

    private ProcessInstancePO testInstance;
    private TaskPO testTask;
    private WorkflowDefinitionPO testDefinition;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testInstance = new ProcessInstancePO();
        testInstance.setId(1L);
        testInstance.setWorkflowDefinitionId(100L);
        testInstance.setProcessCode("leave_approval");
        testInstance.setTitle("张三的请假申请");
        testInstance.setStatus("RUNNING");
        testInstance.setInitiatorId(10L);
        testInstance.setBusinessKey("LEAVE-2026-001");
        testInstance.setCurrentNodeCode("node_2");
        testInstance.setStartTime(LocalDateTime.now().minusHours(2));
        testInstance.setCreateTime(LocalDateTime.now().minusHours(2));

        testTask = new TaskPO();
        testTask.setId(1L);
        testTask.setProcessInstanceId(1L);
        testTask.setNodeId("node_2");
        testTask.setTaskName("部门主管审批");
        testTask.setStatus("PENDING");
        testTask.setAssigneeId(20L);
        testTask.setCreateTime(LocalDateTime.now().minusHours(1));

        testDefinition = new WorkflowDefinitionPO();
        testDefinition.setId(100L);
        testDefinition.setCode("leave_approval");
        testDefinition.setName("请假审批流程");
        testDefinition.setNodeDefinition("{\"nodes\":[],\"edges\":[]}");
        testDefinition.setVersion(1);
        testDefinition.setEnabled(1);
    }

    @Test
    @DisplayName("listInstances - 成功分页查询流程实例")
    void testListInstances_Success() {
        // Given: 模拟分页查询结果
        Page<ProcessInstancePO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(testInstance));
        page.setTotal(1);

        when(processInstanceMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);
        when(workflowDefinitionMapper.selectById(100L)).thenReturn(testDefinition);

        // When: 调用查询服务
        Mono<PageResponse<WorkflowInstanceDTO>> result = queryService.listInstances(null, 1, 10);

        // Then: 验证结果
        StepVerifier.create(result)
                .assertNext(pageResp -> {
                    assertThat(pageResp.getTotal()).isEqualTo(1);
                    assertThat(pageResp.getRecords()).hasSize(1);

                    WorkflowInstanceDTO dto = pageResp.getRecords().get(0);
                    assertThat(dto.getId()).isEqualTo(1L);
                    assertThat(dto.getProcessCode()).isEqualTo("leave_approval");
                    assertThat(dto.getTitle()).isEqualTo("张三的请假申请");
                    assertThat(dto.getStatus()).isEqualTo("RUNNING");
                    assertThat(dto.getCurrentNodeId()).isEqualTo("node_2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("listInstances - 按状态筛选")
    void testListInstances_FilterByStatus() {
        // Given: 模拟筛选查询
        Page<ProcessInstancePO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(testInstance));
        page.setTotal(1);

        when(processInstanceMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);
        when(workflowDefinitionMapper.selectById(100L)).thenReturn(testDefinition);

        // When: 按RUNNING状态筛选
        Mono<PageResponse<WorkflowInstanceDTO>> result = queryService.listInstances("RUNNING", 1, 10);

        // Then: 验证结果
        StepVerifier.create(result)
                .assertNext(pageResp -> {
                    assertThat(pageResp.getTotal()).isEqualTo(1);
                    assertThat(pageResp.getRecords()).hasSize(1);
                    assertThat(pageResp.getRecords().get(0).getStatus()).isEqualTo("RUNNING");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("listInstances - 使用持久化挂起标记")
    void testListInstances_UsesPersistedSuspendedFlag() {
        ProcessInstancePO suspendedInstance = new ProcessInstancePO();
        suspendedInstance.setId(2L);
        suspendedInstance.setWorkflowDefinitionId(100L);
        suspendedInstance.setStatus("RUNNING");
        suspendedInstance.setSuspended(true);
        suspendedInstance.setInitiatorId(10L);

        Page<ProcessInstancePO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(suspendedInstance));
        page.setTotal(1);

        when(processInstanceMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);
        when(workflowDefinitionMapper.selectById(100L)).thenReturn(testDefinition);

        StepVerifier.create(queryService.listInstances(null, 1, 10))
                .assertNext(pageResp ->
                        assertThat(pageResp.getRecords().get(0).getSuspended()).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("listInstances - 兼容挂起状态历史数据")
    void testListInstances_FallsBackToSuspendedStatus() {
        ProcessInstancePO suspendedInstance = new ProcessInstancePO();
        suspendedInstance.setId(3L);
        suspendedInstance.setWorkflowDefinitionId(100L);
        suspendedInstance.setStatus("SUSPENDED");
        suspendedInstance.setSuspended(false);
        suspendedInstance.setInitiatorId(10L);

        Page<ProcessInstancePO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(suspendedInstance));
        page.setTotal(1);

        when(processInstanceMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);
        when(workflowDefinitionMapper.selectById(100L)).thenReturn(testDefinition);

        StepVerifier.create(queryService.listInstances(null, 1, 10))
                .assertNext(pageResp ->
                        assertThat(pageResp.getRecords().get(0).getSuspended()).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("getInstanceDetail - 成功查询流程实例详情")
    void testGetInstanceDetail_Success() {
        // Given: 模拟查询流程实例、定义和任务
        when(processInstanceMapper.selectById(1L)).thenReturn(testInstance);
        when(workflowDefinitionMapper.selectById(100L)).thenReturn(testDefinition);
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(testTask));

        // When: 调用详情查询
        Mono<WorkflowInstanceDetailDTO> result = queryService.getInstanceDetail(1L);

        // Then: 验证结果
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.getInstance()).isNotNull();
                    assertThat(dto.getInstance().getId()).isEqualTo(1L);

                    assertThat(dto.getDefinition()).isNotNull();
                    assertThat(dto.getDefinition().getCode()).isEqualTo("leave_approval");
                    assertThat(dto.getDefinition().getName()).isEqualTo("请假审批流程");

                    assertThat(dto.getTasks()).hasSize(1);
                    assertThat(dto.getTasks().get(0).getTaskName()).isEqualTo("部门主管审批");

                    assertThat(dto.getCurrentNodeId()).isEqualTo("node_2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getInstanceDetail - 流程实例不存在")
    void testGetInstanceDetail_NotFound() {
        // Given: 模拟查询不到流程实例
        when(processInstanceMapper.selectById(999L)).thenReturn(null);

        // When: 调用详情查询
        Mono<WorkflowInstanceDetailDTO> result = queryService.getInstanceDetail(999L);

        // Then: 应返回空
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    @DisplayName("listMyTasks - 成功查询当前用户待办任务")
    void testListMyTasks_Success() {
        // Given: 模拟分页查询待办任务
        Page<TaskPO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(testTask));
        page.setTotal(1);

        when(taskMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        // When: 调用查询服务
        Mono<PageResponse<WorkflowTaskDTO>> result = queryService.listMyTasks(20L, 1, 10);

        // Then: 验证结果
        StepVerifier.create(result)
                .assertNext(pageResp -> {
                    assertThat(pageResp.getTotal()).isEqualTo(1);
                    assertThat(pageResp.getRecords()).hasSize(1);

                    WorkflowTaskDTO dto = pageResp.getRecords().get(0);
                    assertThat(dto.getId()).isEqualTo(1L);
                    assertThat(dto.getTaskName()).isEqualTo("部门主管审批");
                    assertThat(dto.getStatus()).isEqualTo("PENDING");
                    assertThat(dto.getAssigneeId()).isEqualTo(20L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getDefinitionDetail - 成功查询流程定义详情")
    void testGetDefinitionDetail_Success() {
        // Given: 模拟查询流程定义
        when(workflowDefinitionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testDefinition);

        // When: 调用定义详情查询
        Mono<WorkflowDefinitionDTO> result = queryService.getDefinitionDetail("leave_approval");

        // Then: 验证结果
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(100L);
                    assertThat(dto.getCode()).isEqualTo("leave_approval");
                    assertThat(dto.getName()).isEqualTo("请假审批流程");
                    assertThat(dto.getVersion()).isEqualTo(1);
                    assertThat(dto.getEnabled()).isTrue();
                    assertThat(dto.getNodes()).isNotNull();
                    assertThat(dto.getEdges()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getDefinitionDetail - 流程定义不存在")
    void testGetDefinitionDetail_NotFound() {
        // Given: 模拟查询不到流程定义
        when(workflowDefinitionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When: 调用定义详情查询
        Mono<WorkflowDefinitionDTO> result = queryService.getDefinitionDetail("non_existent");

        // Then: 应返回空
        StepVerifier.create(result).verifyComplete();
    }
}
