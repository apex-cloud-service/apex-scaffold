package com.apex.scaffold.common.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 日志安全脱敏工具。
 *
 * <p>提供文本截断、URI 去参、JSON 序列化脱敏等能力，用于在记录日志前
 * 清除控制字符、排除敏感字段并限制长度，防止日志注入与敏感信息泄漏。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since  1.3.0
 */
@SuppressWarnings("PMD.ClassMustHaveAuthorRule")
public final class LogSafetyUtil {
    private static final int MAX_FIELD_NAME = 256;
    private static final int MAX_DEPTH = 6;
    private static final int MAX_ITEMS = 32;
    private static final String APPLICATION_PACKAGE = "com.apex.";
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private LogSafetyUtil() {}

    /**
     * 截断文本至指定长度，并将控制字符替换为空格。
     *
     * @param value 原始文本，null 返回空串
     * @param limit 最大长度，非正数返回空串
     * @return 安全的日志文本
     */
    public static String text(String value, int limit) {
        if (value == null || limit <= 0) {
            return "";
        }
        int end = Math.min(value.length(), Math.min(limit, 8192));
        StringBuilder result = new StringBuilder(end);
        for (int i = 0; i < end; i++) {
            char character = value.charAt(i);
            result.append(Character.isISOControl(character) ? ' ' : character);
        }
        return result.toString();
    }

    /**
     * 截取 URI 的路径部分（去除 query 与 fragment），并按 limit 截断。
     *
     * @param value 原始 URI，null 返回空串
     * @param limit 最大长度
     * @return 安全的日志 URI
     */
    public static String uri(String value, int limit) {
        if (value == null) {
            return "";
        }
        int end = value.indexOf('?');
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            end = end < 0 ? fragment : Math.min(end, fragment);
        }
        return text(
                value.substring(
                        0, end < 0 ? Math.min(value.length(), Math.max(0, limit)) : Math.min(end, Math.max(0, limit))),
                limit);
    }

    /**
     * 将对象序列化为受限长度的 JSON 文本，支持按字段名排除敏感属性。
     *
     * @param value    待序列化对象；字符串按文本截断处理
     * @param limit    最大长度
     * @param excluded 需要排除的字段名列表
     * @return 安全的 JSON 日志文本
     */
    public static String json(Object value, int limit, String... excluded) {
        StringBuilder output = new StringBuilder();
        Set<String> exclusions = new java.util.HashSet<>();
        if (excluded != null) {
            for (String name : excluded) {
                exclusions.add(normalize(name));
            }
        }
        try {
            append(
                    value instanceof String ? "[omitted]" : value,
                    output,
                    Math.max(0, Math.min(limit, 8192)),
                    exclusions,
                    new IdentityHashMap<>(),
                    0);
            String result = output.toString();
            JSON.readTree(result);
            return result;
        } catch (java.io.IOException | RuntimeException ignored) {
            return limit >= 4 ? "null" : "";
        }
    }

    private static String normalize(String name) {
        return name == null
                ? ""
                : text(name, MAX_FIELD_NAME).replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static boolean sensitive(String name, Set<String> exclusions) {
        if (name != null && name.length() > MAX_FIELD_NAME) {
            return true;
        }
        String normalized = normalize(name);
        return exclusions.contains(normalized)
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("credential")
                || normalized.contains("privatekey")
                || normalized.contains("certificate")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("phone")
                || normalized.contains("email")
                || normalized.contains("idcard")
                || "code".equals(normalized)
                || "otp".equals(normalized)
                || "pin".equals(normalized)
                || "apikey".equals(normalized)
                || "assertion".equals(normalized);
    }

    private static void write(String value, StringBuilder output, int limit) {
        int remaining = limit - output.length();
        if (remaining > 0) {
            output.append(value, 0, Math.min(remaining, value.length()));
        }
    }

    private static void quote(String value, StringBuilder output, int limit) {
        write("\"", output, limit);
        int length = Math.min(value.length(), limit);
        for (int i = 0; i < length && output.length() < limit; i++) {
            char character = value.charAt(i);
            if (character == '"' || character == '\\') {
                write("\\", output, limit);
            }
            write(String.valueOf(Character.isISOControl(character) ? ' ' : character), output, limit);
        }
        write("\"", output, limit);
    }

    private static void append(
            Object value,
            StringBuilder output,
            int limit,
            Set<String> exclusions,
            IdentityHashMap<Object, Boolean> seen,
            int depth) {
        if (output.length() >= limit) {
            return;
        }
        if (value == null) {
            write("null", output, limit);
        } else if (value instanceof String || value instanceof Enum<?>) {
            quote(
                    text(value instanceof Enum<?> enumeration ? enumeration.name() : (String) value, limit),
                    output,
                    limit);
        } else if (value instanceof Double number && !Double.isFinite(number)) {
            write("null", output, limit);
        } else if (value instanceof Float number && !Float.isFinite(number)) {
            write("null", output, limit);
        } else if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof Boolean) {
            write(text(value.toString(), 64), output, limit);
        } else if (depth >= MAX_DEPTH || seen.put(value, Boolean.TRUE) != null) {
            quote("[omitted]", output, limit);
        } else {
            try {
                appendContainer(value, output, limit, exclusions, seen, depth);
            } finally {
                seen.remove(value);
            }
        }
    }

    private static void appendContainer(
            Object value,
            StringBuilder output,
            int limit,
            Set<String> exclusions,
            IdentityHashMap<Object, Boolean> seen,
            int depth) {
        if (value instanceof Map<?, ?> map) {
            write("{", output, limit);
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= MAX_ITEMS || output.length() >= limit) {
                    break;
                }
                if (count > 1) {
                    write(",", output, limit);
                }
                String name = entry.getKey() instanceof String key ? text(key, 128) : "[key]";
                quote(name, output, limit);
                write(":", output, limit);
                append(
                        sensitive(entry.getKey() instanceof String key ? key : "", exclusions)
                                ? "***"
                                : entry.getValue(),
                        output,
                        limit,
                        exclusions,
                        seen,
                        depth + 1);
            }
            write("}", output, limit);
        } else if (value instanceof Iterable<?> iterable) {
            write("[", output, limit);
            int count = 0;
            for (Object item : iterable) {
                if (count++ >= MAX_ITEMS || output.length() >= limit) {
                    break;
                }
                if (count > 1) {
                    write(",", output, limit);
                }
                append(item instanceof String ? "[omitted]" : item, output, limit, exclusions, seen, depth + 1);
            }
            write("]", output, limit);
        } else if (value.getClass().isArray()) {
            write("[", output, limit);
            for (int i = 0; i < Math.min(Array.getLength(value), MAX_ITEMS) && output.length() < limit; i++) {
                if (i > 0) {
                    write(",", output, limit);
                }
                Object item = Array.get(value, i);
                append(item instanceof String ? "[omitted]" : item, output, limit, exclusions, seen, depth + 1);
            }
            write("]", output, limit);
        } else if (value.getClass().getName().startsWith(APPLICATION_PACKAGE)) {
            appendFields(value, output, limit, exclusions, seen, depth);
        } else {
            quote("[omitted]", output, limit);
        }
    }

    private static void appendFields(
            Object value,
            StringBuilder output,
            int limit,
            Set<String> exclusions,
            IdentityHashMap<Object, Boolean> seen,
            int depth) {
        write("{", output, limit);
        int count = 0;
        for (Class<?> type = value.getClass();
                type != null && count < MAX_ITEMS && output.length() < limit;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (count++ >= MAX_ITEMS || output.length() >= limit) {
                    break;
                }
                if (count > 1) {
                    write(",", output, limit);
                }
                quote(field.getName(), output, limit);
                write(":", output, limit);
                Object item = "[omitted]";
                try {
                    if (sensitive(field.getName(), exclusions)) {
                        item = "***";
                    } else if (field.trySetAccessible()) {
                        item = field.get(value);
                    }
                } catch (IllegalAccessException ignored) {
                    item = "[omitted]";
                }
                append(item, output, limit, exclusions, seen, depth + 1);
            }
        }
        write("}", output, limit);
    }
}
