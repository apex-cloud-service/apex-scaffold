package com.apex.scaffold.integration.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.integration.device.domain.RuleCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 规则条件评估测试（方案 M5 / 批次 E3）。
 *
 * <p>验证操作符白名单矩阵（数值/字符串/嵌套路径/IN/CONTAINS）、
 * 非法操作符与非法字段拒绝，以及注入安全（字段仅做 Map 取值，无表达式求值面）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("规则条件评估测试")
class RuleConditionEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Object> metric() {
        Map<String, Object> tags = new HashMap<>();
        tags.put("level", "warn");
        Map<String, Object> metric = new HashMap<>();
        metric.put("temperature", 75.5);
        metric.put("humidity", 60);
        metric.put("state", "running");
        metric.put("tags", tags);
        metric.put("codes", List.of("A1", "B2"));
        return metric;
    }

    @Test
    @DisplayName("数值比较矩阵：=,!=,>,>=,<,<=（数值优先）")
    void numericOperatorMatrix() {
        Map<String, Object> metric = metric();
        assertThat(matches("{\"field\":\"temperature\",\"op\":\">\",\"value\":70}"))
                .isTrue();
        assertThat(matches("{\"field\":\"temperature\",\"op\":\"<\",\"value\":70}", metric))
                .isFalse();
        assertThat(matches("{\"field\":\"temperature\",\"op\":\">=\",\"value\":75.5}", metric))
                .isTrue();
        assertThat(matches("{\"field\":\"temperature\",\"op\":\"<=\",\"value\":75.4}", metric))
                .isFalse();
        assertThat(matches("{\"field\":\"temperature\",\"op\":\"=\",\"value\":75.5}", metric))
                .isTrue();
        assertThat(matches("{\"field\":\"temperature\",\"op\":\"!=\",\"value\":75.5}", metric))
                .isFalse();
    }

    @Test
    @DisplayName("字符串比较与嵌套路径、别名操作符（EQ/NE 大小写不敏感）")
    void stringAndNestedPath() {
        Map<String, Object> metric = metric();
        assertThat(matches("{\"field\":\"state\",\"op\":\"=\",\"value\":\"running\"}", metric))
                .isTrue();
        assertThat(matches("{\"field\":\"state\",\"op\":\"=\",\"value\":\"stopped\"}", metric))
                .isFalse();
        assertThat(matches("{\"field\":\"tags.level\",\"op\":\"EQ\",\"value\":\"warn\"}", metric))
                .isTrue();
        assertThat(matches("{\"field\":\"tags.level\",\"op\":\"ne\",\"value\":\"error\"}", metric))
                .isTrue();
        assertThat(matches("{\"field\":\"tags.missing\",\"op\":\"=\",\"value\":\"x\"}", metric))
                .isFalse();
    }

    @Test
    @DisplayName("IN 与 CONTAINS（数组元素/字符串子串）")
    void inAndContains() {
        Map<String, Object> metric = metric();
        assertThat(matches("{\"field\":\"state\",\"op\":\"IN\",\"value\":[\"stopped\",\"running\"]}", metric))
                .isTrue();
        assertThat(matches("{\"field\":\"state\",\"op\":\"IN\",\"value\":[\"stopped\",\"idle\"]}", metric))
                .isFalse();
        assertThat(matches("{\"field\":\"temperature\",\"op\":\"IN\",\"value\":[75.5,100]}", metric))
                .isTrue();
        assertThat(matches("{\"field\":\"state\",\"op\":\"CONTAINS\",\"value\":\"run\"}", metric))
                .isTrue();
        assertThat(matches("{\"field\":\"codes\",\"op\":\"CONTAINS\",\"value\":\"B2\"}", metric))
                .isTrue();
        assertThat(matches("{\"field\":\"codes\",\"op\":\"CONTAINS\",\"value\":\"C3\"}", metric))
                .isFalse();
    }

    @Test
    @DisplayName("数组条件 AND 语义全命中")
    void andSemantics() {
        Map<String, Object> metric = metric();
        List<RuleCondition> conditions = RuleCondition.parse(
                objectMapper,
                "[{\"field\":\"temperature\",\"op\":\">\",\"value\":70},{\"field\":\"state\",\"op\":\"=\",\"value\":\"running\"}]");
        assertThat(RuleCondition.matchesAll(conditions, metric)).isTrue();

        List<RuleCondition> failed = RuleCondition.parse(
                objectMapper,
                "[{\"field\":\"temperature\",\"op\":\">\",\"value\":70},{\"field\":\"state\",\"op\":\"=\",\"value\":\"stopped\"}]");
        assertThat(RuleCondition.matchesAll(failed, metric)).isFalse();
    }

    @Test
    @DisplayName("操作符不在白名单被拒绝（含表达式注入尝试）")
    void unknownOperatorRejected() {
        assertThatThrownBy(() ->
                        RuleCondition.parse(objectMapper, "{\"field\":\"temperature\",\"op\":\"eval\",\"value\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("操作符不在白名单");
        assertThatThrownBy(() -> RuleCondition.parse(
                        objectMapper, "{\"field\":\"temperature\",\"op\":\"; DROP TABLE iot_rule;--\",\"value\":1}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("字段非法字符被拒绝（杜绝路径注入）")
    void illegalFieldRejected() {
        assertThatThrownBy(() -> RuleCondition.parse(
                        objectMapper, "{\"field\":\"temperature; malicious()\",\"op\":\">\",\"value\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("字段非法");
        assertThatThrownBy(() -> RuleCondition.parse(objectMapper, "{\"field\":\"\",\"op\":\">\",\"value\":1}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("非对象条件 JSON 与缺失 value 被拒绝")
    void malformedConditionRejected() {
        assertThatThrownBy(() -> RuleCondition.parse(objectMapper, "\"just-string\""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuleCondition.parse(objectMapper, "{\"field\":\"temperature\",\"op\":\">\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuleCondition.parse(objectMapper, "not-json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 解析并对默认指标求值单条件。 */
    private boolean matches(String conditionJson) {
        return matches(conditionJson, metric());
    }

    private boolean matches(String conditionJson, Map<String, Object> metric) {
        List<RuleCondition> conditions = RuleCondition.parse(objectMapper, conditionJson);
        return RuleCondition.matchesAll(conditions, metric);
    }
}
