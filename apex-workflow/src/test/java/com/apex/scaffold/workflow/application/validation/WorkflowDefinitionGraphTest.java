package com.apex.scaffold.workflow.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 首期可执行 DAG 的解析与拒绝路径，覆盖 T16「非法图/未知节点拒绝」。 */
@DisplayName("流程定义 DAG 校验")
class WorkflowDefinitionGraphTest {

    @Test
    @DisplayName("单链流程可解析并暴露开始、后继与审批人")
    void parsesSingleChain() {
        WorkflowDefinitionGraph graph = WorkflowDefinitionGraph.parse(singleChain("INITIATOR", null));

        assertThat(graph.start().nodeId()).isEqualTo("start");
        WorkflowDefinitionGraph.Node approval = graph.next(graph.start());
        assertThat(approval.nodeId()).isEqualTo("approve");
        assertThat(approval.isApproval()).isTrue();
        assertThat(graph.node("missing")).isNull();

        WorkflowDefinitionGraph.Node end = graph.next(approval);
        assertThat(end.isEnd()).isTrue();
        assertThat(graph.next(end)).isNull();

        assertThat(WorkflowDefinitionGraph.resolveAssignee(approval, 100L)).isEqualTo(100L);
    }

    @Test
    @DisplayName("指定用户审批人取配置的用户ID")
    void resolvesSpecifiedUserApprover() {
        WorkflowDefinitionGraph graph = WorkflowDefinitionGraph.parse(singleChain("SPECIFIED_USER", "42"));

        assertThat(WorkflowDefinitionGraph.resolveAssignee(graph.next(graph.start()), 100L))
                .isEqualTo(42L);
    }

    @ParameterizedTest(name = "空白或不合法 JSON 拒绝：{0}")
    @ValueSource(strings = {"", "   ", "not-json", "[1,2]"})
    void rejectsMissingOrMalformedJson(String dagJson) {
        assertInvalid(dagJson, "流程定义不合法");
    }

    @Test
    @DisplayName("未知节点类型被拒绝")
    void rejectsUnknownNodeType() {
        String dag =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"parallel","nodeType":"PARALLEL"},
                {"nodeId":"end","nodeType":"END"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"start","targetNodeId":"parallel"},
                {"edgeId":"e2","sourceNodeId":"parallel","targetNodeId":"end"}]}
                """;

        assertInvalid(dag, "nodeType 不受支持：PARALLEL");
    }

    @Test
    @DisplayName("重复节点ID被拒绝")
    void rejectsDuplicateNodeId() {
        String dag =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"start","nodeType":"END"}]}
                """;

        assertInvalid(dag, "nodeId 重复");
    }

    @Test
    @DisplayName("缺少审批策略或策略不受支持被拒绝")
    void rejectsUnsupportedApprover() {
        assertInvalid(singleChain(null, null), "缺少 approverType");
        assertInvalid(singleChain("MANAGER", null), "approverType 不受支持：MANAGER");
        assertInvalid(singleChain("SPECIFIED_USER", "abc"), "approverValue 必须是单个正整数用户 ID");
        assertInvalid(singleChain("SPECIFIED_USER", "0"), "approverValue 必须是单个正整数用户 ID");
    }

    @Test
    @DisplayName("连线指向未知节点被拒绝")
    void rejectsEdgeToUnknownNode() {
        String dag =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"approve","nodeType":"APPROVAL","approverType":"INITIATOR"},
                {"nodeId":"end","nodeType":"END"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"start","targetNodeId":"approve"},
                {"edgeId":"e2","sourceNodeId":"approve","targetNodeId":"nowhere"}]}
                """;

        assertInvalid(dag, "targetNodeId 指向不存在的节点：nowhere");
    }

    @Test
    @DisplayName("条件分支（多出边）被拒绝")
    void rejectsMultipleOutgoingEdges() {
        String dag =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"approve","nodeType":"APPROVAL","approverType":"INITIATOR"},
                {"nodeId":"end1","nodeType":"END"},
                {"nodeId":"end2","nodeType":"END"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"start","targetNodeId":"approve"},
                {"edgeId":"e2","sourceNodeId":"approve","targetNodeId":"end1"},
                {"edgeId":"e3","sourceNodeId":"approve","targetNodeId":"end2"}]}
                """;

        assertInvalid(dag, "存在多条出边");
    }

    @Test
    @DisplayName("自连环路被拒绝")
    void rejectsSelfLoop() {
        String dag =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"approve","nodeType":"APPROVAL","approverType":"INITIATOR"},
                {"nodeId":"end","nodeType":"END"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"start","targetNodeId":"approve"},
                {"edgeId":"e2","sourceNodeId":"approve","targetNodeId":"approve"}]}
                """;

        assertInvalid(dag, "不允许自连");
    }

    @Test
    @DisplayName("不可达节点被拒绝")
    void rejectsUnreachableNode() {
        String dag =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"end","nodeType":"END"},
                {"nodeId":"a","nodeType":"APPROVAL","approverType":"INITIATOR"},
                {"nodeId":"b","nodeType":"APPROVAL","approverType":"INITIATOR"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"start","targetNodeId":"end"},
                {"edgeId":"e2","sourceNodeId":"a","targetNodeId":"b"},
                {"edgeId":"e3","sourceNodeId":"b","targetNodeId":"a"}]}
                """;

        assertInvalid(dag, "无法从开始节点到达的节点：a,b");
    }

    @Test
    @DisplayName("缺少开始或结束节点被拒绝")
    void rejectsMissingStartOrEnd() {
        String noStart =
                """
                {"nodes":[{"nodeId":"approve","nodeType":"APPROVAL","approverType":"INITIATOR"},
                {"nodeId":"end","nodeType":"END"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"approve","targetNodeId":"end"}]}
                """;
        String noEnd =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"approve","nodeType":"APPROVAL","approverType":"INITIATOR"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"start","targetNodeId":"approve"}]}
                """;

        assertInvalid(noStart, "缺少开始节点");
        assertInvalid(noEnd, "缺少结束节点");
    }

    @Test
    @DisplayName("结束节点出边与开始节点入边被拒绝")
    void rejectsEndOutgoingAndStartIncoming() {
        String endWithEdge =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"end","nodeType":"END"},
                {"nodeId":"end2","nodeType":"END"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"start","targetNodeId":"end"},
                {"edgeId":"e2","sourceNodeId":"end","targetNodeId":"end2"}]}
                """;
        String startWithIncoming =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"approve","nodeType":"APPROVAL","approverType":"INITIATOR"},
                {"nodeId":"end","nodeType":"END"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"start","targetNodeId":"approve"},
                {"edgeId":"e2","sourceNodeId":"approve","targetNodeId":"start"}]}
                """;

        assertInvalid(endWithEdge, "不应有出边");
        assertInvalid(startWithIncoming, "不应有入边");
    }

    @Test
    @DisplayName("非正数超时小时被拒绝")
    void rejectsNonPositiveTimeoutHours() {
        String dag =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"approve","nodeType":"APPROVAL","approverType":"INITIATOR","timeoutHours":0},
                {"nodeId":"end","nodeType":"END"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"start","targetNodeId":"approve"},
                {"edgeId":"e2","sourceNodeId":"approve","targetNodeId":"end"}]}
                """;

        assertInvalid(dag, "timeoutHours 必须大于 0");
    }

    @Test
    @DisplayName("执行器支持的超时动作与单办理人策略被接受并保留在节点上")
    void acceptsExecutableApprovalFields() {
        String dag =
                """
                {"nodes":[{"nodeId":"start","nodeType":"START"},
                {"nodeId":"approve","nodeType":"APPROVAL","approverType":"SPECIFIED_USER","approverValue":"42",
                "approvalStrategy":"SINGLE","timeoutHours":24,"timeoutAction":"AUTO_COMPLETE"},
                {"nodeId":"end","nodeType":"END"}],
                "edges":[{"edgeId":"e1","sourceNodeId":"start","targetNodeId":"approve"},
                {"edgeId":"e2","sourceNodeId":"approve","targetNodeId":"end"}]}
                """;

        WorkflowDefinitionGraph graph = WorkflowDefinitionGraph.parse(dag);
        WorkflowDefinitionGraph.Node approval = graph.next(graph.start());

        assertThat(approval.approvalStrategy()).isEqualTo("SINGLE");
        assertThat(approval.timeoutHours()).isEqualTo(24);
        assertThat(approval.timeoutAction()).isEqualTo("AUTO_COMPLETE");
    }

    @Test
    @DisplayName("留空审批策略与超时动作时节点字段保持为空")
    void keepsOptionalApprovalFieldsNull() {
        WorkflowDefinitionGraph graph = WorkflowDefinitionGraph.parse(singleChain("INITIATOR", null));
        WorkflowDefinitionGraph.Node approval = graph.next(graph.start());

        assertThat(approval.approvalStrategy()).isNull();
        assertThat(approval.timeoutHours()).isNull();
        assertThat(approval.timeoutAction()).isNull();
    }

    @ParameterizedTest(name = "不受支持的超时动作被拒绝：{0}")
    @ValueSource(strings = {"REMIND", "AUTO_APPROVE", "AUTO_REJECT", "auto_complete"})
    void rejectsUnsupportedTimeoutAction(String timeoutAction) {
        assertInvalid(
                withApprovalFields("\"timeoutHours\":24,\"timeoutAction\":\"" + timeoutAction + "\""),
                "timeoutAction 不受支持：" + timeoutAction);
    }

    @Test
    @DisplayName("设置超时动作但缺少超时小时数被拒绝")
    void rejectsTimeoutActionWithoutHours() {
        assertInvalid(withApprovalFields("\"timeoutAction\":\"ESCALATE\""), "设置 timeoutAction 时必须同时设置 timeoutHours");
    }

    @ParameterizedTest(name = "不受支持的审批策略被拒绝：{0}")
    @ValueSource(strings = {"OR", "AND", "PARALLEL"})
    void rejectsUnsupportedApprovalStrategy(String approvalStrategy) {
        assertInvalid(
                withApprovalFields("\"approvalStrategy\":\"" + approvalStrategy + "\""),
                "approvalStrategy 不受支持：" + approvalStrategy);
    }

    private static void assertInvalid(String dagJson, String messageFragment) {
        assertThatThrownBy(() -> WorkflowDefinitionGraph.parse(dagJson))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程定义不合法")
                .hasMessageContaining(messageFragment);
    }

    /** 构造开始 → 审批 → 结束的单链 DAG，审批节点附带额外字段片段。 */
    private static String withApprovalFields(String extraFields) {
        return "{\"nodes\":[{\"nodeId\":\"start\",\"nodeType\":\"START\"},"
                + "{\"nodeId\":\"approve\",\"nodeType\":\"APPROVAL\",\"approverType\":\"INITIATOR\","
                + extraFields
                + "},{\"nodeId\":\"end\",\"nodeType\":\"END\"}],"
                + "\"edges\":[{\"edgeId\":\"e1\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"approve\"},"
                + "{\"edgeId\":\"e2\",\"sourceNodeId\":\"approve\",\"targetNodeId\":\"end\"}]}";
    }

    /** 构造开始 → 审批 → 结束的单链 DAG。 */
    private static String singleChain(String approverType, String approverValue) {
        StringBuilder approval =
                new StringBuilder("{\"nodeId\":\"approve\",\"nodeType\":\"APPROVAL\",\"nodeName\":\"审批\"");
        if (approverType != null) {
            approval.append(",\"approverType\":\"").append(approverType).append('"');
        }
        if (approverValue != null) {
            approval.append(",\"approverValue\":\"").append(approverValue).append('"');
        }
        approval.append('}');
        return "{\"nodes\":[{\"nodeId\":\"start\",\"nodeType\":\"START\"},"
                + approval
                + ",{\"nodeId\":\"end\",\"nodeType\":\"END\"}],"
                + "\"edges\":[{\"edgeId\":\"e1\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"approve\"},"
                + "{\"edgeId\":\"e2\",\"sourceNodeId\":\"approve\",\"targetNodeId\":\"end\"}]}";
    }
}
