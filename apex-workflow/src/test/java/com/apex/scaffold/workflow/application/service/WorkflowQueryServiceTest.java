package com.apex.scaffold.workflow.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.ProcessInstancePO;
import com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO;
import com.apex.scaffold.workflow.infrastructure.persistence.po.WorkflowDefinitionPO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 工作流查询服务的租户与 DTO 契约测试。
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
        for (Class<?> type : List.of(ProcessInstancePO.class, TaskPO.class, WorkflowDefinitionPO.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
        testInstance = new ProcessInstancePO();
        testInstance.setId(1L);
        testInstance.setWorkflowDefinitionId(100L);
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
        testDefinition = new WorkflowDefinitionPO();
        testDefinition.setId(100L);
        testDefinition.setCode("leave_approval");
        testDefinition.setName("请假审批流程");
        testDefinition.setNodeDefinition("{\"nodes\":[],\"edges\":[]}");
        testDefinition.setVersion(1);
        testDefinition.setEnabled(1);
    }

    static Stream<String> queries() {
        return Stream.of(
                "instances", "instancesFiltered", "instance", "tasks", "task", "definitionId", "definitionCode");
    }

    private Mono<?> query(String name) {
        return switch (name) {
            case "instances" -> queryService.listInstances(null, 1, 10);
            case "instancesFiltered" -> queryService.listInstances(1, 10, "RUNNING", 10L);
            case "instance" -> queryService.getInstanceDetail(1L);
            case "tasks" -> queryService.listMyTasks(20L, 1, 10);
            case "task" -> queryService.getTaskById(1L);
            case "definitionId" -> queryService.getDefinitionDetail(100L);
            case "definitionCode" -> queryService.getDefinitionDetail("leave_approval");
            default -> throw new IllegalArgumentException(name);
        };
    }

    @ParameterizedTest
    @MethodSource("queries")
    void rejectsMissingTenantWithoutMapperAccess(String name) {
        String previous = TenantContext.setTenantId("thread-local-must-not-authorize");
        try {
            StepVerifier.create(query(name)).expectErrorMessage("当前请求未包含有效租户").verify();
            verifyNoInteractions(processInstanceMapper, taskMapper, workflowDefinitionMapper);
        } finally {
            TenantContext.restoreTenantId(previous);
        }
    }

    @ParameterizedTest
    @MethodSource("queries")
    void rejectsBlankTenantWithoutMapperAccess(String name) {
        StepVerifier.create(query(name).contextWrite(context -> context.put(TenantContext.REACTOR_CONTEXT_KEY, " ")))
                .expectErrorMessage("当前请求未包含有效租户")
                .verify();
        verifyNoInteractions(processInstanceMapper, taskMapper, workflowDefinitionMapper);
    }

    private void assertTenant(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment()).contains("tenant_id");
        assertThat(wrapper.getParamNameValuePairs()).containsValue("tenant-a");
    }

    @Test
    void testListInstances_Success() {
        Page<ProcessInstancePO> page = new Page<>(1, 10);
        page.setRecords(List.of(testInstance));
        page.setTotal(1);
        when(processInstanceMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(call -> {
                    LambdaQueryWrapper<?> wrapper = call.getArgument(1);
                    assertTenant(wrapper);
                    assertThat(wrapper.getSqlSegment()).contains("status", "initiator_id");
                    return page;
                });
        when(workflowDefinitionMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            assertTenant(call.getArgument(0));
            return testDefinition;
        });
        StepVerifier.create(queryService
                        .listInstances(1, 10, "RUNNING", 10L)
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .assertNext(result -> {
                    assertThat(result.getTotal()).isEqualTo(1);
                    assertThat(result.getRecords()).hasSize(1);
                    var dto = result.getRecords().get(0);
                    assertThat(dto.getProcessCode()).isEqualTo("leave_approval");
                    assertThat(dto.getTitle()).isEqualTo("张三的请假申请");
                    assertThat(dto.getCurrentNodeId()).isEqualTo("node_2");
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void testListInstances_Suspended(boolean persisted) {
        testInstance.setSuspended(persisted);
        testInstance.setStatus(persisted ? "RUNNING" : "SUSPENDED");
        Page<ProcessInstancePO> page = new Page<>(1, 10);
        page.setRecords(List.of(testInstance));
        when(processInstanceMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);
        StepVerifier.create(queryService.listInstances(null, 1, 10).contextWrite(TenantContext.withTenant("tenant-a")))
                .assertNext(result -> {
                    assertThat(result.getRecords().get(0).getSuspended()).isTrue();
                    assertThat(result.getRecords().get(0).getProcessCode()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void testGetInstanceDetail_Success() {
        when(processInstanceMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            assertTenant(call.getArgument(0));
            return testInstance;
        });
        when(workflowDefinitionMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            assertTenant(call.getArgument(0));
            return testDefinition;
        });
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            assertTenant(call.getArgument(0));
            return List.of(testTask);
        });
        StepVerifier.create(queryService.getInstanceDetail(1L).contextWrite(TenantContext.withTenant("tenant-a")))
                .assertNext(dto -> {
                    assertThat(dto.getInstance().getId()).isEqualTo(1L);
                    assertThat(dto.getDefinition().getCode()).isEqualTo("leave_approval");
                    assertThat(dto.getTasks()).hasSize(1);
                    assertThat(dto.getTasks().get(0).getTaskName()).isEqualTo("部门主管审批");
                    assertThat(dto.getCurrentNodeId()).isEqualTo("node_2");
                })
                .verifyComplete();
    }

    @Test
    void testListMyTasks_Success() {
        Page<TaskPO> page = new Page<>(1, 10);
        page.setRecords(List.of(testTask));
        page.setTotal(1);
        when(taskMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(call -> {
                    assertTenant(call.getArgument(1));
                    return page;
                });
        StepVerifier.create(queryService.listMyTasks(20L, 1, 10).contextWrite(TenantContext.withTenant("tenant-a")))
                .assertNext(result -> {
                    assertThat(result.getTotal()).isEqualTo(1);
                    assertThat(result.getRecords().get(0).getAssigneeId()).isEqualTo(20L);
                })
                .verifyComplete();
    }

    @Test
    void testGetTaskById_Success() {
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            assertTenant(call.getArgument(0));
            return testTask;
        });
        StepVerifier.create(queryService.getTaskById(1L).contextWrite(TenantContext.withTenant("tenant-a")))
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(1L);
                    assertThat(dto.getInstanceId()).isEqualTo(1L);
                    assertThat(dto.getNodeId()).isEqualTo("node_2");
                    assertThat(dto.getStatus()).isEqualTo("PENDING");
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"definitionId", "definitionCode"})
    void testGetDefinitionDetail_Success(String name) {
        when(workflowDefinitionMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            assertTenant(call.getArgument(0));
            return testDefinition;
        });
        StepVerifier.create(query(name)
                        .cast(com.apex.scaffold.workflow.application.dto.WorkflowDefinitionDTO.class)
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(100L);
                    assertThat(dto.getName()).isEqualTo("请假审批流程");
                    assertThat(dto.getVersion()).isEqualTo(1);
                    assertThat(dto.getEnabled()).isTrue();
                    assertThat(dto.getNodes()).isEmpty();
                    assertThat(dto.getEdges()).isEmpty();
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"instance", "task", "definitionId", "definitionCode"})
    void returnsEmptyWhenNotFound(String name) {
        StepVerifier.create(query(name).contextWrite(TenantContext.withTenant("tenant-a")))
                .verifyComplete();
    }
}
