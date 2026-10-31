package com.apex.scaffold.workflow.application.query;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.application.dto.WorkflowDefinitionDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowInstanceDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowInstanceDetailDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowTaskDTO;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinitionStatus;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.ProcessInstancePO;
import com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO;
import com.apex.scaffold.workflow.infrastructure.persistence.po.WorkflowDefinitionPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 工作流查询服务。
 *
 * <p>提供流程实例查询、任务查询、详情查询等功能。</p>
 *
 * <p>该服务基于响应式编程模型，所有查询方法均返回 {@link Mono}，
 * 内部对 MyBatis-Plus 的阻塞式 Mapper 调用统一通过
 * {@link BlockingIoSchedulers#jdbc()} 调度器承载，避免阻塞 Netty 事件循环线程。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>流程实例分页查询与状态过滤</li>
 *   <li>流程实例详情聚合查询（定义 + 任务 + 当前节点）</li>
 *   <li>用户待办任务分页查询</li>
 *   <li>流程定义详情查询（用于设计器加载）</li>
 *   <li>数据库持久化对象向应用 DTO 的转换</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowQueryService {

    /** 流程实例 Mapper，负责流程实例表的持久化查询 */
    private final ProcessInstanceMapper processInstanceMapper;

    /** 任务 Mapper，负责任务表的持久化查询 */
    private final TaskMapper taskMapper;

    /** 工作流定义 Mapper，负责流程定义表的持久化查询 */
    private final WorkflowDefinitionMapper workflowDefinitionMapper;

    /** Jackson JSON 解析器，用于解析流程定义的 DAG JSON 结构 */
    private final ObjectMapper objectMapper;

    /**
     * 分页查询流程实例列表。
     *
     * <p>按状态过滤流程实例，按创建时间倒序排列。该方法仅为便捷重载，
     * 内部委托给携带发起人过滤参数的完整版本。
     *
     * @param status 状态过滤（可选）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public Mono<PageResponse<WorkflowInstanceDTO>> listInstances(String status, int pageNum, int pageSize) {
        return listInstances(pageNum, pageSize, status, null);
    }

    /**
     * 分页查询流程实例列表（完整版本）。
     *
     * <p>支持按状态与发起人双重过滤，查询结果按创建时间倒序排列。
     * 每条实例记录会额外查询其所属工作流定义以补充流程编码信息。
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param status 状态过滤（可选）
     * @param initiatorId 发起人过滤（可选）
     * @return 分页结果
     */
    public Mono<PageResponse<WorkflowInstanceDTO>> listInstances(
            int pageNum, int pageSize, String status, Long initiatorId) {
        return queryForTenant(tenantId -> {
                    log.info(
                            "开始查询工作流实例列表: pageNum={}, pageSize={}, status={}, initiatorId={}",
                            pageNum,
                            pageSize,
                            status,
                            initiatorId);

                    Page<ProcessInstancePO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<ProcessInstancePO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ProcessInstancePO::getTenantId, tenantId);

                    if (status != null && !status.isEmpty()) {
                        wrapper.eq(ProcessInstancePO::getStatus, status);
                    }
                    if (initiatorId != null) {
                        wrapper.eq(ProcessInstancePO::getInitiatorId, initiatorId);
                    }
                    wrapper.orderByDesc(ProcessInstancePO::getCreateTime);

                    IPage<ProcessInstancePO> poPage = processInstanceMapper.selectPage(page, wrapper);
                    log.info("查询到 {} 条记录，总数: {}", poPage.getRecords().size(), poPage.getTotal());

                    // 转换为 DTO，需要查询工作流定义获取 processCode
                    List<WorkflowInstanceDTO> dtoList = poPage.getRecords().stream()
                            .map(po -> {
                                WorkflowDefinitionPO definition = workflowDefinitionMapper.selectOne(
                                        new LambdaQueryWrapper<WorkflowDefinitionPO>()
                                                .eq(WorkflowDefinitionPO::getId, po.getWorkflowDefinitionId())
                                                .eq(WorkflowDefinitionPO::getTenantId, tenantId));
                                return convertToInstanceDTO(po, definition);
                            })
                            .collect(Collectors.toList());

                    log.info("转换完成，返回 {} 条 DTO", dtoList.size());
                    return PageResponse.of(dtoList, poPage.getTotal(), pageNum, pageSize);
                })
                .doOnError(error -> log.error("查询工作流实例列表失败", error));
    }

    /**
     * 获取流程实例详情（包含定义和任务）。
     *
     * <p>聚合流程实例基本信息、流程定义、任务列表与当前节点，
     * 用于详情页完整渲染。若实例不存在则返回 null。
     *
     * @param instanceId 流程实例 ID
     * @return 流程详情 DTO
     */
    public Mono<WorkflowInstanceDetailDTO> getInstanceDetail(Long instanceId) {
        return queryForTenant(tenantId -> {
            WorkflowInstanceDetailDTO detail = new WorkflowInstanceDetailDTO();

            // 查询流程实例
            ProcessInstancePO processInstance =
                    processInstanceMapper.selectOne(new LambdaQueryWrapper<ProcessInstancePO>()
                            .eq(ProcessInstancePO::getId, instanceId)
                            .eq(ProcessInstancePO::getTenantId, tenantId));
            if (processInstance == null) {
                return null;
            }

            // 查询流程定义
            WorkflowDefinitionPO workflowDefinition =
                    workflowDefinitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionPO>()
                            .eq(WorkflowDefinitionPO::getId, processInstance.getWorkflowDefinitionId())
                            .eq(WorkflowDefinitionPO::getTenantId, tenantId));
            detail.setInstance(convertToInstanceDTO(processInstance, workflowDefinition));
            detail.setCurrentNodeId(processInstance.getCurrentNodeCode());

            if (workflowDefinition != null) {
                detail.setDefinition(convertToDefinitionDTO(workflowDefinition));
            }

            // 查询任务列表
            List<TaskPO> taskList = taskMapper.selectList(new LambdaQueryWrapper<TaskPO>()
                    .eq(TaskPO::getProcessInstanceId, instanceId)
                    .eq(TaskPO::getTenantId, tenantId)
                    .orderByDesc(TaskPO::getCreateTime));
            List<WorkflowTaskDTO> tasks =
                    taskList.stream().map(this::convertToTaskDTO).collect(Collectors.toList());
            detail.setTasks(tasks);

            return detail;
        });
    }

    /**
     * 查询用户待办任务。
     *
     * <p>按办理人 ID 查询状态为 PENDING 的待办任务，按创建时间倒序分页返回。
     *
     * @param assigneeId 办理人 ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public Mono<PageResponse<WorkflowTaskDTO>> listMyTasks(Long assigneeId, int pageNum, int pageSize) {
        return queryForTenant(tenantId -> {
            Page<TaskPO> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<TaskPO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TaskPO::getAssigneeId, assigneeId)
                    .eq(TaskPO::getStatus, "PENDING")
                    .eq(TaskPO::getTenantId, tenantId)
                    .orderByDesc(TaskPO::getCreateTime);
            IPage<TaskPO> poPage = taskMapper.selectPage(page, wrapper);

            List<WorkflowTaskDTO> dtoList =
                    poPage.getRecords().stream().map(this::convertToTaskDTO).collect(Collectors.toList());

            return PageResponse.of(dtoList, poPage.getTotal(), pageNum, pageSize);
        });
    }

    /**
     * 按任务 ID 查询任务详情。
     *
     * <p>用于在工作流事件发布等场景补充任务信息（所属流程实例、节点编码等）。
     * 主键不存在时返回空 {@link Mono}。
     *
     * @param taskId 任务 ID
     * @return 任务 DTO；任务不存在时返回 {@link Mono#empty()}
     */
    public Mono<WorkflowTaskDTO> getTaskById(Long taskId) {
        return queryForTenant(tenantId -> {
            TaskPO po = taskMapper.selectOne(
                    new LambdaQueryWrapper<TaskPO>().eq(TaskPO::getId, taskId).eq(TaskPO::getTenantId, tenantId));
            return po == null ? null : convertToTaskDTO(po);
        });
    }

    /**
     * 获取流程定义详情（用于设计器加载）。
     *
     * <p>根据流程编码查询唯一的流程定义，并解析其 DAG JSON 结构。
     *
     * @param workflowCode 流程编码
     * @return 流程定义 DTO
     */
    public Mono<WorkflowDefinitionDTO> getDefinitionDetail(String workflowCode) {
        return queryForTenant(tenantId -> {
            WorkflowDefinitionPO workflowDefinition =
                    workflowDefinitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionPO>()
                            .eq(WorkflowDefinitionPO::getWorkflowCode, workflowCode)
                            .eq(WorkflowDefinitionPO::getTenantId, tenantId)
                            .orderByDesc(WorkflowDefinitionPO::getVersion)
                            .last("LIMIT 1"));
            if (workflowDefinition == null) {
                return null;
            }
            return convertToDefinitionDTO(workflowDefinition);
        });
    }

    /**
     * 获取流程定义详情（根据定义 ID）。
     *
     * <p>根据流程定义主键 ID 查询流程定义，并解析其 DAG JSON 结构。
     *
     * @param definitionId 流程定义 ID
     * @return 流程定义 DTO
     */
    public Mono<WorkflowDefinitionDTO> getDefinitionDetail(Long definitionId) {
        return queryForTenant(tenantId -> {
            WorkflowDefinitionPO workflowDefinition =
                    workflowDefinitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionPO>()
                            .eq(WorkflowDefinitionPO::getId, definitionId)
                            .eq(WorkflowDefinitionPO::getTenantId, tenantId));
            if (workflowDefinition == null) {
                return null;
            }
            return convertToDefinitionDTO(workflowDefinition);
        });
    }

    /**
     * 分页查询流程定义（用于定义管理列表）。
     *
     * <p>按流程编码与生命周期状态过滤，最近更新的记录排在前面。
     * 列表项同样解析 DAG JSON，便于设计器直接渲染缩略图。</p>
     *
     * @param workflowCode 流程编码过滤条件，为空表示不过滤
     * @param status       生命周期状态过滤条件，为空表示不过滤
     * @param pageNum      页码
     * @param pageSize     每页大小
     * @return 分页结果
     */
    public Mono<PageResponse<WorkflowDefinitionDTO>> listDefinitions(
            String workflowCode, String status, int pageNum, int pageSize) {
        WorkflowDefinitionStatus parsedStatus = parseStatus(status);
        return queryForTenant(tenantId -> {
            Page<WorkflowDefinitionPO> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<WorkflowDefinitionPO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(WorkflowDefinitionPO::getTenantId, tenantId)
                    .eq(
                            workflowCode != null && !workflowCode.isBlank(),
                            WorkflowDefinitionPO::getWorkflowCode,
                            workflowCode)
                    .eq(
                            parsedStatus != null,
                            WorkflowDefinitionPO::getStatus,
                            parsedStatus == null ? null : parsedStatus.name())
                    .orderByDesc(WorkflowDefinitionPO::getUpdateTime);

            IPage<WorkflowDefinitionPO> poPage = workflowDefinitionMapper.selectPage(page, wrapper);
            List<WorkflowDefinitionDTO> dtoList = poPage.getRecords().stream()
                    .map(this::convertToDefinitionDTO)
                    .collect(Collectors.toList());
            return PageResponse.of(dtoList, poPage.getTotal(), pageNum, pageSize);
        });
    }

    /**
     * 查询指定定义所属流程编码的全部版本。
     *
     * <p>定义不存在或不属于当前租户时返回空列表，由接口层按 404 处理，
     * 避免跨租户探测定义是否存在。</p>
     *
     * @param definitionId 流程定义 ID
     * @return 版本列表，按版本号降序
     */
    public Mono<List<WorkflowDefinitionDTO>> listDefinitionVersions(Long definitionId) {
        return queryForTenant(tenantId -> {
            WorkflowDefinitionPO definition =
                    workflowDefinitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionPO>()
                            .eq(WorkflowDefinitionPO::getId, definitionId)
                            .eq(WorkflowDefinitionPO::getTenantId, tenantId));
            if (definition == null) {
                return List.<WorkflowDefinitionDTO>of();
            }
            List<WorkflowDefinitionPO> versions =
                    workflowDefinitionMapper.selectList(new LambdaQueryWrapper<WorkflowDefinitionPO>()
                            .eq(WorkflowDefinitionPO::getWorkflowCode, definition.getWorkflowCode())
                            .eq(WorkflowDefinitionPO::getTenantId, tenantId)
                            .orderByDesc(WorkflowDefinitionPO::getVersion));
            return versions.stream().map(this::convertToDefinitionDTO).collect(Collectors.toList());
        });
    }

    private WorkflowDefinitionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return WorkflowDefinitionStatus.from(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的生命周期状态：" + status);
        }
    }

    private <T> Mono<T> queryForTenant(Function<String, T> query) {
        return Mono.deferContextual(context -> {
            String tenantId = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenantId == null || tenantId.isBlank()) {
                return Mono.error(new IllegalStateException("当前请求未包含有效租户"));
            }
            return Mono.fromCallable(() -> query.apply(tenantId)).subscribeOn(BlockingIoSchedulers.jdbc());
        });
    }

    /**
     * 将流程实例持久化对象转换为流程实例 DTO。
     *
     * <p>流程编码取自关联的工作流定义而非业务键；发起人姓名暂采用简化规则拼接。
     *
     * @param po 流程实例持久化对象
     * @param definition 关联的工作流定义持久化对象（可能为 null）
     * @return 流程实例 DTO
     */
    private WorkflowInstanceDTO convertToInstanceDTO(ProcessInstancePO po, WorkflowDefinitionPO definition) {
        WorkflowInstanceDTO dto = new WorkflowInstanceDTO();
        dto.setId(po.getId());
        dto.setDefinitionId(po.getWorkflowDefinitionId());
        // 从工作流定义获取 processCode，而不是使用 businessKey
        dto.setProcessCode(definition != null ? definition.getWorkflowCode() : null);
        dto.setTitle(po.getRemark() != null ? po.getRemark() : "流程实例-" + po.getId());
        dto.setStatus(po.getStatus());
        dto.setSuspended(Boolean.TRUE.equals(po.getSuspended()) || "SUSPENDED".equals(po.getStatus()));
        dto.setInitiatorId(po.getInitiatorId());
        // 简化映射，实际应关联用户表
        dto.setInitiatorName("用户-" + po.getInitiatorId());
        dto.setBusinessKey(po.getBusinessKey());
        dto.setCurrentNodeId(po.getCurrentNodeCode());
        dto.setStartTime(po.getStartTime());
        dto.setEndTime(po.getEndTime());
        dto.setCreateTime(po.getCreateTime());
        return dto;
    }

    /**
     * 将任务持久化对象转换为任务 DTO。
     *
     * <p>任务类型暂固定为 USER_TASK，办理人姓名采用简化规则拼接。
     *
     * @param po 任务持久化对象
     * @return 任务 DTO
     */
    private WorkflowTaskDTO convertToTaskDTO(TaskPO po) {
        WorkflowTaskDTO dto = new WorkflowTaskDTO();
        dto.setId(po.getId());
        dto.setInstanceId(po.getProcessInstanceId());
        dto.setTaskName(po.getTaskName());
        // 简化映射
        dto.setTaskType("USER_TASK");
        dto.setStatus(po.getStatus());
        dto.setAssigneeId(po.getAssigneeId());
        // 简化映射
        dto.setAssigneeName("用户-" + po.getAssigneeId());
        dto.setComment(po.getRemark());
        dto.setNodeId(po.getNodeCode());
        dto.setStartTime(po.getCreateTime());
        dto.setCompletedTime(po.getCompleteTime());
        dto.setCreateTime(po.getCreateTime());
        return dto;
    }

    /**
     * 将工作流定义持久化对象转换为定义 DTO。
     *
     * <p>会解析 {@code node_definition} 字段中的 DAG JSON 结构，
     * 提取其中节点列表与连线列表。解析失败时返回空的节点与连线列表。
     *
     * @param po 工作流定义持久化对象
     * @return 流程定义 DTO
     */
    private WorkflowDefinitionDTO convertToDefinitionDTO(WorkflowDefinitionPO po) {
        WorkflowDefinitionDTO dto = new WorkflowDefinitionDTO();
        dto.setId(po.getId());
        dto.setCode(po.getWorkflowCode());
        dto.setName(po.getWorkflowName());
        dto.setDescription(po.getDescription());
        dto.setVersion(po.getVersion());
        dto.setEnabled(Integer.valueOf(1).equals(po.getEnabled()));
        dto.setStatus(WorkflowDefinitionStatus.from(po.getStatus()).name());
        dto.setPublishedAt(po.getPublishedAt());
        dto.setPublishedBy(po.getPublishedBy());

        // 解析 DAG JSON，增加 null 防护
        try {
            if (po.getNodeDefinition() != null && !po.getNodeDefinition().isEmpty()) {
                Map<String, Object> dag =
                        objectMapper.readValue(po.getNodeDefinition(), new TypeReference<Map<String, Object>>() {});

                // 解析节点，增加 null 检查
                if (dag != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> nodesJson = (List<Map<String, Object>>) dag.get("nodes");
                    if (nodesJson != null) {
                        List<WorkflowDefinitionDTO.WorkflowNodeDTO> nodes =
                                nodesJson.stream().map(this::convertToNodeDTO).collect(Collectors.toList());
                        dto.setNodes(nodes);
                    } else {
                        dto.setNodes(new ArrayList<>(16));
                    }

                    // 解析连线
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> edgesJson = (List<Map<String, Object>>) dag.get("edges");
                    if (edgesJson != null) {
                        List<WorkflowDefinitionDTO.WorkflowEdgeDTO> edges =
                                edgesJson.stream().map(this::convertToEdgeDTO).collect(Collectors.toList());
                        dto.setEdges(edges);
                    } else {
                        dto.setEdges(new ArrayList<>(16));
                    }
                } else {
                    dto.setNodes(new ArrayList<>(16));
                    dto.setEdges(new ArrayList<>(16));
                }
            } else {
                dto.setNodes(new ArrayList<>(16));
                dto.setEdges(new ArrayList<>(16));
            }
        } catch (Exception e) {
            log.error("解析 DAG JSON 失败", e);
            dto.setNodes(new ArrayList<>(16));
            dto.setEdges(new ArrayList<>(16));
        }

        return dto;
    }

    /**
     * 将节点 JSON 数据转换为节点 DTO。
     *
     * <p>从 JSON 中提取节点的标识、类型、名称、审批人配置、超时配置与坐标位置等字段。
     * 坐标与超时时长按容忍类型漂移的方式提取：单节点字段异常只降级该字段，
     * 不沿外层兜底扩散成整图丢失。
     *
     * @param json 节点 JSON 数据
     * @return 节点 DTO
     */
    private WorkflowDefinitionDTO.WorkflowNodeDTO convertToNodeDTO(Map<String, Object> json) {
        WorkflowDefinitionDTO.WorkflowNodeDTO node = new WorkflowDefinitionDTO.WorkflowNodeDTO();
        node.setNodeId((String) json.get("nodeId"));
        node.setNodeType((String) json.get("nodeType"));
        node.setNodeName((String) json.get("nodeName"));
        node.setApproverType((String) json.get("approverType"));
        node.setApproverValue((String) json.get("approverValue"));
        node.setApprovalStrategy((String) json.get("approvalStrategy"));
        node.setTimeoutHours(asInteger(json.get("timeoutHours")));
        node.setTimeoutAction((String) json.get("timeoutAction"));
        node.setPosition(extractPosition(json));
        return node;
    }

    /**
     * 提取节点坐标信息，容忍历史数据中的类型漂移。
     *
     * <p>position 在历史定义中可能被序列化为字符串或其他非对象结构（例如早期设计器
     * 直接存 JSON 字符串）。此时保留节点其余字段并返回空坐标并记录告警，
     * 而不是让单节点的类型异常把整张定义图收敛为空。
     *
     * @param json 节点 JSON 数据
     * @return 坐标映射；无法按对象解析时返回空映射
     */
    private Map<String, Object> extractPosition(Map<String, Object> json) {
        Object raw = json.get("position");
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> position = (Map<String, Object>) raw;
            return position;
        }
        if (raw != null) {
            log.warn(
                    "节点 position 字段类型异常，已忽略坐标: nodeId={}, actualType={}",
                    json.get("nodeId"),
                    raw.getClass().getSimpleName());
        }
        return new HashMap<>(16);
    }

    /**
     * 以数值方式提取整型字段，容忍 Jackson 将大数值解析为 Long、小数解析为 Double。
     *
     * @param raw 原始 JSON 值
     * @return 整型值；非数值时返回 {@code null}
     */
    private Integer asInteger(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    /**
     * 将连线 JSON 数据转换为连线 DTO。
     *
     * <p>从 JSON 中提取连线的标识、源节点、目标节点与流转条件字段。
     *
     * @param json 连线 JSON 数据
     * @return 连线 DTO
     */
    private WorkflowDefinitionDTO.WorkflowEdgeDTO convertToEdgeDTO(Map<String, Object> json) {
        WorkflowDefinitionDTO.WorkflowEdgeDTO edge = new WorkflowDefinitionDTO.WorkflowEdgeDTO();
        edge.setEdgeId((String) json.get("edgeId"));
        edge.setSourceNodeId((String) json.get("sourceNodeId"));
        edge.setTargetNodeId((String) json.get("targetNodeId"));
        edge.setCondition((String) json.get("condition"));
        return edge;
    }
}
