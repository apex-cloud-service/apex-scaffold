package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

/**
 * Multipart 请求解析工具组件。
 *
 * <p>封装 {@code multipart/form-data} 请求的通用解析逻辑，包括文件字段提取、
 * 表单字段读取、布尔/整数类型安全转换等常用操作。消除多个 Handler 中重复的
 * {@code multipart / singleFile / requiredFile / formData / field / booleanField / intField / toResource}
 * 私有方法。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Component
public class MultipartRequestParser {

    /**
     * 将 HTTP 请求解析为 multipart 字段映射。
     *
     * @param request 待解析的 WebFlux 服务器请求
     * @return multipart 数据映射，key 为字段名，value 为 Part 列表
     */
    public Mono<MultiValueMap<String, Part>> multipart(ServerRequest request) {
        return request.multipartData();
    }

    /**
     * 从 multipart 字段映射中获取指定名称的必填文件，缺失时抛出参数错误异常。
     *
     * @param parts multipart 字段映射
     * @param name  字段名称
     * @return 匹配到的 Part 对象
     * @throws BusinessException 当指定名称的文件字段不存在时
     */
    public Part requiredFile(MultiValueMap<String, Part> parts, String name) {
        Part part = parts.getFirst(name);
        if (part == null) throw new BusinessException(ResultCode.PARAM_ERROR, "缺少文件字段: " + name);
        return part;
    }

    /**
     * 从 multipart 字段映射中获取指定名称的可选文件，不存在时返回 null。
     *
     * @param parts multipart 字段映射
     * @param name  字段名称
     * @return 匹配到的 Part 对象，不存在时返回 null
     */
    public Part file(MultiValueMap<String, Part> parts, String name) {
        return parts.getFirst(name);
    }

    /**
     * 提取所有非文件字段为不可变 Map。
     *
     * <p>遍历 multipart 字段映射，提取所有 {@link FormFieldPart} 类型的字段，
     * 返回字段名到字段值的不可变映射。</p>
     *
     * @param parts multipart 字段映射
     * @return 表单字段名值对的不可变 Map
     */
    public Map<String, String> formData(MultiValueMap<String, Part> parts) {
        Map<String, String> fields = new LinkedHashMap<>();
        parts.forEach((name, values) -> values.stream()
                .filter(FormFieldPart.class::isInstance)
                .map(FormFieldPart.class::cast)
                .findFirst()
                .ifPresent(part -> fields.put(name, part.value())));
        return Map.copyOf(fields);
    }

    /**
     * 从表单字段中获取必填字符串值，缺失或为空时抛出参数错误异常。
     *
     * @param parts multipart 字段映射
     * @param name  字段名称
     * @return 非空字符串值
     * @throws BusinessException 当字段缺失或为空时
     */
    public String requiredField(MultiValueMap<String, Part> parts, String name) {
        String value = field(parts, name, null);
        if (value == null || value.isBlank()) throw new BusinessException(ResultCode.PARAM_ERROR, "缺少表单字段: " + name);
        return value;
    }

    /**
     * 从表单字段中获取字符串值，当字段不存在或不是表单字段时返回默认值。
     *
     * @param parts        multipart 字段映射
     * @param name         字段名称
     * @param defaultValue 字段缺失时的默认值
     * @return 字符串值或默认值
     */
    public String field(MultiValueMap<String, Part> parts, String name, String defaultValue) {
        Part part = parts.getFirst(name);
        return part instanceof FormFieldPart f ? f.value() : defaultValue;
    }

    /**
     * 从表单字段中获取布尔值，通过 {@link Boolean#parseBoolean(String)} 解析。
     *
     * @param parts        multipart 字段映射
     * @param name         字段名称
     * @param defaultValue 字段缺失时的默认值
     * @return 布尔值或默认值
     */
    public boolean booleanField(MultiValueMap<String, Part> parts, String name, boolean defaultValue) {
        String value = field(parts, name, null);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * 从表单字段中获取整数值，字段值无法解析为整数时抛出参数错误异常。
     *
     * @param parts        multipart 字段映射
     * @param name         字段名称
     * @param defaultValue 字段缺失时的默认值
     * @return 整数值或默认值
     * @throws BusinessException 当字段值不是合法整数时
     */
    public int intField(MultiValueMap<String, Part> parts, String name, int defaultValue) {
        String value = field(parts, name, null);
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, name + "必须是整数");
        }
    }

    /**
     * 将 {@link FilePart} 内容读取为 {@link ByteArrayResource}，空文件时返回错误。
     *
     * @param filePart multipart 中的文件部分
     * @return 包含文件字节和原始文件名的资源对象
     */
    public Mono<Resource> toResource(FilePart filePart) {
        return DataBufferUtils.join(filePart.content()).flatMap(buffer -> {
            try {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                if (bytes.length == 0) return parameterError("文件不能为空: " + filePart.filename());
                return Mono.just(new ByteArrayResource(bytes) {
                    /**
                     * 返回原始的 multipart 文件名。
                     *
                     * @return 原始文件名
                     */
                    @Override
                    public String getFilename() {
                        return filePart.filename();
                    }
                });
            } finally {
                DataBufferUtils.release(buffer);
            }
        });
    }

    /**
     * 构造参数错误信号。
     *
     * @param message 错误提示信息
     * @param <T>     泛型类型
     * @return 携带参数错误的 Mono 信号
     */
    private static <T> Mono<T> parameterError(String message) {
        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, message));
    }
}
