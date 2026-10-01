package com.apex.scaffold.application.support;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 代码模板引擎
 *
 * <p>提供简单的变量替换模板渲染功能，支持 ${variableName} 语法。
 * 作为代码生成的模板引擎，支持将数据模型渲染到模板字符串中。
 *
 * <p><strong>模板语法</strong>：
 * <ul>
 *   <li>${variableName} - 变量替换</li>
 *   <li>${object.property} - 对象属性访问（简单实现）</li>
 * </ul>
 *
 * <p><strong>注意</strong>：当前实现为简化版本，仅支持基本的变量替换。
 * 如需更复杂的模板功能（条件、循环等），可后续集成 FreeMarker 或 Velocity。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class CodeTemplateEngine {

    private static final String DOT_SEPARATOR = ".";

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 渲染模板
     *
     * <p>将数据模型中的变量替换到模板字符串中，返回渲染后的结果。
     *
     * @param templateContent 模板内容（支持 ${variableName} 语法）
     * @param dataModel 数据模型（变量名 → 值映射）
     * @return 渲染后的字符串
     */
    public Mono<String> render(String templateContent, Map<String, Object> dataModel) {
        return Mono.fromCallable(() -> {
                    if (templateContent == null) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "模板内容不能为空");
                    }

                    Matcher matcher = VARIABLE_PATTERN.matcher(templateContent);
                    StringBuffer result = new StringBuffer();

                    while (matcher.find()) {
                        String variableName = matcher.group(1);
                        Object value = resolveVariable(variableName, dataModel);
                        String replacement = value != null ? value.toString() : "";
                        matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                    }

                    matcher.appendTail(result);
                    return result.toString();
                })
                .onErrorMap(e -> {
                    if (e instanceof BusinessException) {
                        return e;
                    }
                    log.error("模板渲染失败", e);
                    return new BusinessException(ResultCode.ERROR, "模板渲染失败: " + e.getMessage());
                });
    }

    /**
     * 解析变量值
     *
     * <p>支持简单的点号属性访问，例如 "user.name" 会先获取 dataModel 中的 "user" 对象，
     * 然后尝试获取其 "name" 属性。
     *
     * @param variableName 变量名（可能包含点号）
     * @param dataModel 数据模型
     * @return 变量值，未找到返回 null
     */
    private Object resolveVariable(String variableName, Map<String, Object> dataModel) {
        if (dataModel == null || variableName == null) {
            return null;
        }

        if (dataModel.containsKey(variableName)) {
            return dataModel.get(variableName);
        }

        if (variableName.contains(DOT_SEPARATOR)) {
            String[] parts = variableName.split("\\.", 2);
            Object obj = dataModel.get(parts[0]);
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) obj;
                return resolveVariable(parts[1], map);
            }
        }

        return null;
    }
}
