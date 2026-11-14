package com.apex.scaffold.workflow.application.validation;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流定义的 DAG 结构解析与校验。
 *
 * <p>首期执行器只支持开始、审批、结束三类节点，因此定义必须是自开始节点出发的
 * 单链结构：每个非结束节点恰有一条出边，链尾必须落到结束节点，且所有节点都可达。
 * 条件分支、未知节点类型、悬空边与环路会被明确拒绝，避免发布无法执行的流程。</p>
 *
 * <p>校验错误统一抛出 {@link BusinessException}，消息中带上节点或连线标识与字段名，
 * 便于设计器定位问题。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class WorkflowDefinitionGraph {

    /** 开始节点类型。 */
    public static final String NODE_START = "START";

    /** 审批节点类型。 */
    public static final String NODE_APPROVAL = "APPROVAL";

    /** 结束节点类型。 */
    public static final String NODE_END = "END";

    /** 指定用户审批人类型。 */
    public static final String APPROVER_SPECIFIED_USER = "SPECIFIED_USER";

    /** 发起人审批人类型。 */
    public static final String APPROVER_INITIATOR = "INITIATOR";

    /** 超时动作：自动通过（执行器按策略自动完成待办）。 */
    public static final String TIMEOUT_AUTO_COMPLETE = "AUTO_COMPLETE";

    /** 超时动作：升级办理人。 */
    public static final String TIMEOUT_ESCALATE = "ESCALATE";

    /** 审批策略：单办理人（首期执行器唯一支持）。 */
    public static final String APPROVAL_STRATEGY_SINGLE = "SINGLE";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INVALID_PREFIX = "流程定义不合法：";

    private final Map<String, Node> nodes;

    private final Map<String, String> successors;

    private final String startNodeId;

    private WorkflowDefinitionGraph(Map<String, Node> nodes, Map<String, String> successors, String startNodeId) {
        this.nodes = nodes;
        this.successors = successors;
        this.startNodeId = startNodeId;
    }

    /**
     * 解析并校验流程定义 JSON。
     *
     * @param dagJson 流程 DAG 的 JSON 文本
     * @return 校验通过的图结构
     * @throws BusinessException 当 JSON 非法或结构不满足执行器约束时
     */
    public static WorkflowDefinitionGraph parse(String dagJson) {
        if (dagJson == null || dagJson.isBlank()) {
            throw invalid("流程定义内容为空");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(dagJson);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.PARAM_ERROR, INVALID_PREFIX + "不是合法的 JSON");
        }
        if (root == null || !root.isObject()) {
            throw invalid("顶层结构必须是 JSON 对象");
        }
        Map<String, Node> nodes = parseNodes(root.get("nodes"));
        List<Edge> edges = parseEdges(root.get("edges"), nodes);
        String startNodeId = validateStartAndEnd(nodes);
        Map<String, String> successors = buildSuccessors(edges, nodes);
        validateChain(nodes, successors, startNodeId);
        return new WorkflowDefinitionGraph(nodes, successors, startNodeId);
    }

    /**
     * 获取开始节点。
     *
     * @return 开始节点
     */
    public Node start() {
        return nodes.get(startNodeId);
    }

    /**
     * 获取指定 ID 的节点。
     *
     * @param nodeId 节点 ID
     * @return 节点，不存在时返回 {@code null}
     */
    public Node node(String nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * 获取节点的唯一后继节点。
     *
     * @param node 当前节点
     * @return 后继节点；当前节点为结束节点时返回 {@code null}
     */
    public Node next(Node node) {
        String nextId = successors.get(node.nodeId());
        return nextId == null ? null : nodes.get(nextId);
    }

    /**
     * 计算审批节点的实际办理人。
     *
     * <p>首期仅支持指定用户与发起人两种策略，指定用户必须是单个正整数用户 ID。</p>
     *
     * @param node        审批节点
     * @param initiatorId 流程发起人用户 ID
     * @return 实际办理人用户 ID
     */
    public static Long resolveAssignee(Node node, Long initiatorId) {
        if (APPROVER_INITIATOR.equals(node.approverType())) {
            return initiatorId;
        }
        return Long.valueOf(node.approverValue().trim());
    }

    private static Map<String, Node> parseNodes(JsonNode nodesNode) {
        if (nodesNode == null || !nodesNode.isArray() || nodesNode.isEmpty()) {
            throw invalid("nodes 必须是非空数组");
        }
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (JsonNode item : nodesNode) {
            if (item == null || !item.isObject()) {
                throw invalid("nodes 中的元素必须是 JSON 对象");
            }
            String nodeId = text(item, "nodeId");
            if (nodeId == null) {
                throw invalid("存在缺少 nodeId 的节点");
            }
            if (nodes.containsKey(nodeId)) {
                throw invalid("节点 " + nodeId + " 的 nodeId 重复");
            }
            String nodeType = text(item, "nodeType");
            if (nodeType == null) {
                throw invalid("节点 " + nodeId + " 缺少 nodeType");
            }
            if (!NODE_START.equals(nodeType) && !NODE_APPROVAL.equals(nodeType) && !NODE_END.equals(nodeType)) {
                throw invalid("节点 " + nodeId + " 的 nodeType 不受支持：" + nodeType);
            }
            String approverType = text(item, "approverType");
            String approverValue = text(item, "approverValue");
            Integer timeoutHours = integer(item, "timeoutHours", nodeId);
            String timeoutAction = text(item, "timeoutAction");
            String approvalStrategy = text(item, "approvalStrategy");
            Node node = new Node(
                    nodeId,
                    nodeType,
                    text(item, "nodeName"),
                    approverType,
                    approverValue,
                    approvalStrategy,
                    timeoutHours,
                    timeoutAction);
            if (NODE_APPROVAL.equals(nodeType)) {
                validateApprover(node);
            }
            nodes.put(nodeId, node);
        }
        return nodes;
    }

    private static void validateApprover(Node node) {
        if (node.approverType() == null) {
            throw invalid("审批节点 " + node.nodeId() + " 缺少 approverType");
        }
        if (APPROVER_INITIATOR.equals(node.approverType())) {
            validateTimeout(node);
            return;
        }
        if (!APPROVER_SPECIFIED_USER.equals(node.approverType())) {
            throw invalid("审批节点 " + node.nodeId() + " 的 approverType 不受支持：" + node.approverType());
        }
        String value = node.approverValue();
        if (value == null || !value.trim().matches("[1-9][0-9]*")) {
            throw invalid("审批节点 " + node.nodeId() + " 的 approverValue 必须是单个正整数用户 ID");
        }
        validateTimeout(node);
    }

    /**
     * 校验审批节点上的设计器字段是否都能被执行器解释。
     *
     * <p>设计器可以画出多项配置，但执行器只按 {@code timeoutAction} 建立超时策略，
     * 且首期只实现单办理人节点。无法执行的取值在保存阶段直接拒绝，避免「能画不能跑」。</p>
     *
     * @param node 审批节点
     */
    private static void validateTimeout(Node node) {
        String action = node.timeoutAction();
        if (action != null && !TIMEOUT_AUTO_COMPLETE.equals(action) && !TIMEOUT_ESCALATE.equals(action)) {
            throw invalid("审批节点 " + node.nodeId() + " 的 timeoutAction 不受支持：" + action);
        }
        if (action != null && node.timeoutHours() == null) {
            throw invalid("审批节点 " + node.nodeId() + " 设置 timeoutAction 时必须同时设置 timeoutHours");
        }
        String strategy = node.approvalStrategy();
        if (strategy != null && !APPROVAL_STRATEGY_SINGLE.equals(strategy)) {
            throw invalid("审批节点 " + node.nodeId() + " 的 approvalStrategy 不受支持：" + strategy + "，首期只支持单办理人审批");
        }
    }

    private static List<Edge> parseEdges(JsonNode edgesNode, Map<String, Node> nodes) {
        if (edgesNode == null || edgesNode.isNull()) {
            return List.of();
        }
        if (!edgesNode.isArray()) {
            throw invalid("edges 必须是数组");
        }
        List<Edge> edges = new ArrayList<>();
        Set<String> edgeIds = new HashSet<>();
        for (JsonNode item : edgesNode) {
            if (item == null || !item.isObject()) {
                throw invalid("edges 中的元素必须是 JSON 对象");
            }
            String edgeId = text(item, "edgeId");
            if (edgeId == null) {
                throw invalid("存在缺少 edgeId 的连线");
            }
            if (!edgeIds.add(edgeId)) {
                throw invalid("连线 " + edgeId + " 的 edgeId 重复");
            }
            String source = text(item, "sourceNodeId");
            String target = text(item, "targetNodeId");
            if (source == null) {
                throw invalid("连线 " + edgeId + " 缺少 sourceNodeId");
            }
            if (target == null) {
                throw invalid("连线 " + edgeId + " 缺少 targetNodeId");
            }
            if (!nodes.containsKey(source)) {
                throw invalid("连线 " + edgeId + " 的 sourceNodeId 指向不存在的节点：" + source);
            }
            if (!nodes.containsKey(target)) {
                throw invalid("连线 " + edgeId + " 的 targetNodeId 指向不存在的节点：" + target);
            }
            if (source.equals(target)) {
                throw invalid("连线 " + edgeId + " 不允许自连");
            }
            edges.add(new Edge(edgeId, source, target));
        }
        return edges;
    }

    private static String validateStartAndEnd(Map<String, Node> nodes) {
        String startNodeId = null;
        int endCount = 0;
        for (Node node : nodes.values()) {
            if (NODE_START.equals(node.nodeType())) {
                if (startNodeId != null) {
                    throw invalid("只能存在一个开始节点");
                }
                startNodeId = node.nodeId();
            } else if (NODE_END.equals(node.nodeType())) {
                endCount++;
            }
        }
        if (startNodeId == null) {
            throw invalid("缺少开始节点");
        }
        if (endCount == 0) {
            throw invalid("缺少结束节点");
        }
        return startNodeId;
    }

    private static Map<String, String> buildSuccessors(List<Edge> edges, Map<String, Node> nodes) {
        Map<String, String> successors = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String nodeId : nodes.keySet()) {
            inDegree.put(nodeId, 0);
        }
        for (Edge edge : edges) {
            String source = edge.sourceNodeId();
            String target = edge.targetNodeId();
            if (successors.containsKey(source)) {
                throw invalid("节点 " + source + " 存在多条出边，首期只支持单链流程");
            }
            if (NODE_END.equals(nodes.get(source).nodeType())) {
                throw invalid("结束节点 " + source + " 不应有出边");
            }
            successors.put(source, target);
            inDegree.merge(target, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            String nodeId = entry.getKey();
            Node node = nodes.get(nodeId);
            if (NODE_START.equals(node.nodeType()) && entry.getValue() > 0) {
                throw invalid("开始节点 " + nodeId + " 不应有入边");
            }
            if (!NODE_START.equals(node.nodeType()) && entry.getValue() == 0) {
                throw invalid("节点 " + nodeId + " 缺少入边");
            }
            if (NODE_END.equals(node.nodeType()) && successors.containsKey(nodeId)) {
                throw invalid("结束节点 " + nodeId + " 不应有出边");
            }
        }
        return successors;
    }

    private static void validateChain(Map<String, Node> nodes, Map<String, String> successors, String startNodeId) {
        Set<String> visited = new HashSet<>();
        String current = startNodeId;
        while (current != null) {
            if (!visited.add(current)) {
                throw invalid("流程定义存在环路，节点 " + current + " 被重复访问");
            }
            Node node = nodes.get(current);
            if (NODE_END.equals(node.nodeType())) {
                break;
            }
            String next = successors.get(current);
            if (next == null) {
                throw invalid("节点 " + current + " 缺少后继连线");
            }
            if (!NODE_END.equals(nodes.get(next).nodeType()) && !successors.containsKey(next)) {
                throw invalid("节点 " + next + " 缺少后继连线");
            }
            current = next;
        }
        if (visited.size() != nodes.size()) {
            List<String> unreachable = nodes.keySet().stream()
                    .filter(nodeId -> !visited.contains(nodeId))
                    .toList();
            throw invalid("存在无法从开始节点到达的节点：" + String.join(",", unreachable));
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static Integer integer(JsonNode node, String field, String nodeId) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isInt() && !value.isLong()) {
            throw invalid("节点 " + nodeId + " 的 " + field + " 必须是整数");
        }
        int number = value.asInt();
        if (number <= 0) {
            throw invalid("节点 " + nodeId + " 的 " + field + " 必须大于 0");
        }
        return number;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ResultCode.PARAM_ERROR, INVALID_PREFIX + message);
    }

    /**
     * 流程节点。
     *
     * @param nodeId           节点 ID
     * @param nodeType         节点类型
     * @param nodeName         节点名称
     * @param approverType     审批人类型
     * @param approverValue    审批人取值
     * @param approvalStrategy 审批策略
     * @param timeoutHours     超时小时数
     * @param timeoutAction    超时动作
     */
    public record Node(
            String nodeId,
            String nodeType,
            String nodeName,
            String approverType,
            String approverValue,
            String approvalStrategy,
            Integer timeoutHours,
            String timeoutAction) {

        /**
         * 判断是否为结束节点。
         *
         * @return 结束节点返回 {@code true}
         */
        public boolean isEnd() {
            return NODE_END.equals(nodeType);
        }

        /**
         * 判断是否为审批节点。
         *
         * @return 审批节点返回 {@code true}
         */
        public boolean isApproval() {
            return NODE_APPROVAL.equals(nodeType);
        }
    }

    /**
     * 流程连线。
     *
     * @param edgeId       连线 ID
     * @param sourceNodeId 源节点 ID
     * @param targetNodeId 目标节点 ID
     */
    private record Edge(String edgeId, String sourceNodeId, String targetNodeId) {}
}
