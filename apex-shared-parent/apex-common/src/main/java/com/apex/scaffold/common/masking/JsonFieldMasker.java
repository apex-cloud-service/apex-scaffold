package com.apex.scaffold.common.masking;

import com.apex.scaffold.common.annotation.MaskType;
import com.apex.scaffold.common.util.DataMaskUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Map;

/**
 * 按精确字段名递归脱敏 JSON 副本，不改变调用方输入。未知字段保持原值。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class JsonFieldMasker {
    private static final int MAX_DEPTH = 64;

    /**
     * 不可变脱敏规则。
     * @param type 类型
     * @param customRule 自定义保留区间
     */
    public record Rule(MaskType type, String customRule) {
        /** 预先校验规则配置。 */
        public Rule {
            if (type == null) {
                throw new IllegalArgumentException("规则类型不能为空");
            }
            if (type == MaskType.CUSTOM) {
                DataMaskUtil.parseCustomRule(customRule);
            }
        }
    }

    /**
     * 创建脱敏副本，选中字段为非字符串时全遮盖为三个星号；null 保持 null。
     * @param input 原 JSON
     * @param rules 精确字段名与规则映射
     * @return 脱敏副本
     */
    public static JsonNode mask(JsonNode input, Map<String, Rule> rules) {
        if (input == null || rules == null) {
            throw new IllegalArgumentException("JSON 与规则不能为空");
        }
        JsonNode result = input.deepCopy();
        visit(result, rules, 0);
        return result;
    }

    /**
     * 递归处理容器并限制深度。
     * @param node 节点
     * @param rules 规则
     * @param depth 深度
     */
    private static void visit(JsonNode node, Map<String, Rule> rules, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("JSON 嵌套过深");
        }
        if (node instanceof ObjectNode object) {
            var fields = object.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                Rule rule = rules.get(field.getKey());
                JsonNode value = field.getValue();
                if (rule != null && !value.isNull()) {
                    String masked = value.isTextual()
                            ? DataMaskUtil.mask(value.textValue(), rule.type(), rule.customRule())
                            : "***";
                    object.set(field.getKey(), TextNode.valueOf(masked));
                } else {
                    visit(value, rules, depth + 1);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                visit(item, rules, depth + 1);
            }
        }
    }
}
