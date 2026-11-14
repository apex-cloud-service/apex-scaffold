package com.apex.scaffold.office.notify.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知模板渲染器（方案 M4 / 批次 D1）。
 *
 * <p>占位符语法 {@code ${key}}：渲染时以参数替换，<b>缺失参数保留原占位符</b>
 * （便于运营发现问题，而非静默输出空串）。不执行表达式/脚本，无注入面。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class NotifyTemplateRenderer {

    /** 占位符模式：${key}，key 允许字母数字下划线与点 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.]+)}");

    private NotifyTemplateRenderer() {}

    /**
     * 渲染模板正文。
     *
     * @param template 模板正文（含 {@code ${key}} 占位符）
     * @param params   参数集合（可空）
     * @return 渲染结果；未提供的占位符原样保留
     */
    public static String render(String template, Map<String, Object> params) {
        if (template == null || template.isBlank()) {
            return "";
        }
        if (params == null || params.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            Object value = params.get(matcher.group(1));
            matcher.appendReplacement(
                    rendered, Matcher.quoteReplacement(value == null ? matcher.group(0) : String.valueOf(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /**
     * 提取模板声明的参数名集合（供模板校验与管理端提示）。
     *
     * @param template 模板正文
     * @return 参数名集合（去重）
     */
    public static java.util.Set<String> declaredParams(String template) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (template == null || template.isBlank()) {
            return names;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
