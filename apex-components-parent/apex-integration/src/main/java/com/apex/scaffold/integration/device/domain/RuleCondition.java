package com.apex.scaffold.integration.device.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 结构化规则条件（方案 M5 / 批次 E3）。
 *
 * <p>仅承载"字段 + 操作符 + 阈值"三元组；操作符限定白名单枚举，字段限定为
 * 遥测 JSON 内的点分路径（逐级 Map 取值），不存在任何表达式/脚本执行面。
 * 阈值以 Jackson {@link JsonNode} 承载（数组支持 IN / CONTAINS）。
 *
 * @param field 遥测字段点分路径（如 {@code temperature}、{@code tags.level}）
 * @param op    操作符
 * @param value 阈值节点
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public record RuleCondition(String field, Operator op, JsonNode value) {

    /**
     * 结构化操作符白名单（禁止扩展为脚本语义）。
     *
     * <p>文本别名与符号等价（{@code EQ} 等同 {@code =}），解析大小写不敏感。
     */
    public enum Operator {
        /** 等于。 */
        EQ("=", "EQ"),
        /** 不等于。 */
        NE("!=", "NE"),
        /** 大于。 */
        GT(">", "GT"),
        /** 大于等于。 */
        GTE(">=", "GTE"),
        /** 小于。 */
        LT("<", "LT"),
        /** 小于等于。 */
        LTE("<=", "LTE"),
        /** 值在阈值数组内。 */
        IN("IN"),
        /** 包含（字符串子串或数组元素）。 */
        CONTAINS("CONTAINS");

        private final String token;

        private final String[] aliases;

        Operator(String token, String... aliases) {
            this.token = token;
            this.aliases = aliases;
        }

        /**
         * 按文本解析操作符（符号或别名，大小写不敏感）。
         *
         * @param token 操作符文本
         * @return 操作符；不在白名单内返回 empty
         */
        public static java.util.Optional<Operator> parse(String token) {
            String normalized = token == null ? "" : token.trim();
            return Arrays.stream(values())
                    .filter(operator -> operator.token.equalsIgnoreCase(normalized)
                            || Arrays.stream(operator.aliases).anyMatch(alias -> alias.equalsIgnoreCase(normalized)))
                    .findFirst();
        }
    }

    /** 字段路径合法性：字母数字/下划线/连字符/点，1~64 位（仅 Map 取值，无求值面）。 */
    private static final String FIELD_PATTERN = "[A-Za-z0-9_\\-.]{1,64}";

    /**
     * 按点分路径从遥测 Map 中解析字段值（逐级 Map 取值，缺失返回 null）。
     *
     * @param metric 遥测指标 Map
     * @return 字段值；路径不存在为 null
     */
    Object resolve(Map<String, Object> metric) {
        Object current = metric;
        for (String segment : field.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 对单个遥测值求值本条件。
     *
     * @param actual 遥测字段值（可空）
     * @return 条件是否命中
     */
    boolean matches(Object actual) {
        return switch (op) {
            case EQ -> compareScalar(actual) == 0;
            case NE -> compareScalar(actual) != 0;
            case GT -> compareScalar(actual) > 0;
            case GTE -> compareScalar(actual) >= 0;
            case LT -> compareScalar(actual) < 0;
            case LTE -> compareScalar(actual) <= 0;
            case IN -> inList(actual);
            case CONTAINS -> contains(actual);
        };
    }

    /**
     * 标量比较：两侧均可数值化时按数值比较，否则按字符串比较。
     *
     * <p>{@code actual} 为 {@code null}（遥测缺少该字段）时返回哨兵值 {@code -2}。
     * 该哨兵值不表示“不命中”：{@code NE}、{@code LT}、{@code LTE} 会因此判为命中，
     * {@code EQ}、{@code GT}、{@code GTE} 判为不命中。若规则要求“字段缺失一律不命中”，
     * 需在规则表达式中显式约束字段存在，或改用 {@code IN} 等空值安全操作符。</p>
     *
     * @param actual 遥测中的实际值
     * @return 负数/0/正数分别表示 actual 小于/等于/大于阈值；{@code actual == null} 时返回 {@code -2}
     */
    private int compareScalar(Object actual) {
        if (actual == null) {
            return -2;
        }
        BigDecimal actualNumber = toNumber(actual);
        BigDecimal thresholdNumber = toDecimal(value);
        if (actualNumber != null && thresholdNumber != null) {
            return actualNumber.compareTo(thresholdNumber);
        }
        return String.valueOf(actual).compareTo(value.asText());
    }

    /** IN：实际值与阈值数组任一元素相等（数值或文本等值）。 */
    private boolean inList(Object actual) {
        if (!value.isArray() || actual == null) {
            return false;
        }
        BigDecimal actualNumber = toNumber(actual);
        for (JsonNode candidate : value) {
            BigDecimal candidateNumber = toDecimal(candidate);
            if (actualNumber != null && candidateNumber != null && actualNumber.compareTo(candidateNumber) == 0) {
                return true;
            }
            if (String.valueOf(actual).equals(candidate.asText())) {
                return true;
            }
        }
        return false;
    }

    /** CONTAINS：字符串为子串包含；数组为元素等值包含。 */
    private boolean contains(Object actual) {
        if (actual instanceof String text && value.isTextual()) {
            return text.contains(value.asText());
        }
        if (actual instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                BigDecimal elementNumber = toNumber(element);
                BigDecimal candidateNumber = toDecimal(value);
                if (elementNumber != null && candidateNumber != null && elementNumber.compareTo(candidateNumber) == 0) {
                    return true;
                }
                if (String.valueOf(element).equals(value.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private BigDecimal toDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            return toNumber(node.asText());
        }
        return null;
    }

    private BigDecimal toNumber(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 解析 condition_json：单对象或对象数组（AND 语义），并做结构与白名单校验。
     *
     * @param objectMapper Jackson 解析器
     * @param conditionJson 条件 JSON 原文
     * @return 条件列表
     * @throws IllegalArgumentException JSON 非法、字段不合法或操作符不在白名单
     */
    public static List<RuleCondition> parse(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper, String conditionJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(conditionJson == null ? "" : conditionJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("规则条件 JSON 非法");
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else if (root.isObject()) {
            nodes.add(root);
        } else {
            throw new IllegalArgumentException("规则条件须为对象或对象数组");
        }
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("规则条件不能为空");
        }
        List<RuleCondition> conditions = new ArrayList<>(nodes.size());
        for (JsonNode node : nodes) {
            JsonNode fieldNode = node.get("field");
            JsonNode opNode = node.get("op");
            if (fieldNode == null || !fieldNode.isTextual() || opNode == null || !opNode.isTextual()) {
                throw new IllegalArgumentException("规则条件缺少 field/op 文本字段");
            }
            String field = fieldNode.asText().trim();
            if (!field.matches(FIELD_PATTERN)) {
                throw new IllegalArgumentException("规则条件字段非法: " + field);
            }
            Operator operator = Operator.parse(opNode.asText())
                    .orElseThrow(() -> new IllegalArgumentException("规则条件操作符不在白名单: " + opNode.asText()));
            JsonNode threshold = node.get("value");
            if (threshold == null || threshold.isNull()) {
                throw new IllegalArgumentException("规则条件缺少 value 阈值");
            }
            conditions.add(new RuleCondition(field, operator, threshold));
        }
        return conditions;
    }

    /**
     * 判断条件列表是否全部命中（AND 语义）。
     *
     * <p>空条件列表视为全部命中，返回 {@code true}；调用方需自行保证条件列表非空，
     * 避免“条件缺失”被当作“无条件命中”。</p>
     *
     * @param conditions 条件列表
     * @param metric     遥测指标 Map
     * @return 是否全部条件命中
     */
    public static boolean matchesAll(List<RuleCondition> conditions, Map<String, Object> metric) {
        for (RuleCondition condition : conditions) {
            if (!condition.matches(condition.resolve(metric))) {
                return false;
            }
        }
        return true;
    }

    /** 供调试输出条件摘要（不含阈值全文，避免日志膨胀）。 */
    static String describe(List<RuleCondition> conditions) {
        StringBuilder summary = new StringBuilder();
        for (Iterator<RuleCondition> iterator = conditions.iterator(); iterator.hasNext(); ) {
            RuleCondition condition = iterator.next();
            summary.append(condition.field).append(' ').append(condition.op).append(" ? ");
            if (iterator.hasNext()) {
                summary.append("AND ");
            }
        }
        return summary.toString();
    }
}
