package com.apex.scaffold.workflow.application.query;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.application.dto.WorkflowDefinitionDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowInstanceDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowInstanceDetailDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowTaskDTO;
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
 * <h2>核心功能</h2>
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
        return Mono.fromCallable(() -> {
                    log.info(
                            "开始查询工作流实例列表: pageNum={}, pageSize={}, status={}, initiatorId={}",
                            pageNum,
                            pageSize,
                            status,
                            initiatorId);

                    Page<ProcessInstancePO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<ProcessInstancePO> wrapper = new LambdaQueryWrapper<>();

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
                                WorkflowDefinitionPO definition =
                                        workflowDefinitionMapper.selectById(po.getWorkflowDefinitionId());
                                return convertToInstanceDTO(po, definition);
                            })
                            .collect(Collectors.toList());

                    log.info("转换完成，返回 {} 条 DTO", dtoList.size());
                    return PageResponse.of(dtoList, poPage.getTotal(), pageNum, pageSize);
                })
                .doOnError(error -> log.error("查询工作流实例列表失败", error))
                .subscribeOn(BlockingIoSchedulers.jdbc());
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
        return Mono.fromCallable(() -> {
                    WorkflowInstanceDetailDTO detail = new WorkflowInstanceDetailDTO();

                    // 查询流程实例
                    ProcessInstancePO processInstance = processInstanceMapper.selectById(instanceId);
                    if (processInstance == null) {
                        return null;
                    }

                    // 查询流程定义
                    WorkflowDefinitionPO workflowDefinition =
                            workflowDefinitionMapper.selectById(processInstance.getWorkflowDefinitionId());
                    detail.setInstance(convertToInstanceDTO(processInstance, workflowDefinition));
                    detail.setCurrentNodeId(processInstance.getCurrentNodeCode());

                    if (workflowDefinition != null) {
                        detail.setDefinition(convertToDefinitionDTO(workflowDefinition));
                    }

                    // 查询任务列表
                    List<TaskPO> taskList = taskMapper.selectList(new LambdaQueryWrapper<TaskPO>()
                            .eq(TaskPO::getProcessInstanceId, instanceId)
                            .orderByDesc(TaskPO::getCreateTime));
                    List<WorkflowTaskDTO> tasks =
                            taskList.stream().map(this::convertToTaskDTO).collect(Collectors.toList());
                    detail.setTasks(tasks);

                    return detail;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
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
        return Mono.fromCallable(() -> {
                    Page<TaskPO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<TaskPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(TaskPO::getAssigneeId, assigneeId)
                            .eq(TaskPO::getStatus, "PENDING")
                            .orderByDesc(TaskPO::getCreateTime);

                    IPage<TaskPO> poPage = taskMapper.selectPage(page, wrapper);

                    List<WorkflowTaskDTO> dtoList = poPage.getRecords().stream()
                            .map(this::convertToTaskDTO)
                            .collect(Collectors.toList());

                    return PageResponse.of(dtoList, poPage.getTotal(), pageNum, pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
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
        return Mono.fromCallable(() -> {
                    WorkflowDefinitionPO workflowDefinition =
                            workflowDefinitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionPO>()
                                    .eq(WorkflowDefinitionPO::getWorkflowCode, workflowCode));
                    if (workflowDefinition == null) {
                        return null;
                    }
                    return convertToDefinitionDTO(workflowDefinition);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
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
        return Mono.fromCallable(() -> {
                    WorkflowDefinitionPO workflowDefinition = workflowDefinitionMapper.selectById(definitionId);
                    if (workflowDefinition == null) {
                        return null;
                    }
                    return convertToDefinitionDTO(workflowDefinition);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
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
        dto.setEnabled(po.getEnabled() == 1);

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
        node.setTimeoutHours((Integer) json.get("timeoutHours"));
        node.setTimeoutAction((String) json.get("timeoutAction"));

        @SuppressWarnings("unchecked")
        Map<String, Object> position = (Map<String, Object>) json.get("position");
        node.setPosition(position != null ? position : new HashMap<>(16));

        return node;
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
