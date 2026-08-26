package com.apex.scaffold.interfaces.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 生产环境配置校验器抽象基类。
 *
 * <p>为所有 {@code *ConfigValidator} 提供统一的工具方法（{@code isBlank}、{@code requireText}、
 * {@code invalid} 等）和 {@link PostConstruct} 生命周期模板。子类仅需实现 {@link #doValidate()}
 * 方法即可。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class AbstractProductionConfigValidator {

    /** 占位/默认凭据值集合，生产环境不得使用。 */
    protected static final Set<String> PLACEHOLDER_VALUES =
            Set.of("minioadmin", "admin", "password", "your-access-key", "your-secret-key");

    /**
     * Spring 容器初始化后调用的校验入口，委托子类实现具体的校验逻辑。
     *
     * <p>由 {@link PostConstruct} 驱动，在 Bean 属性注入完成后自动执行。
     * 子类通过覆写 {@link #doValidate()} 实现各自的生产就绪检查。</p>
     */
    @PostConstruct
    public void validate() {
        doValidate();
    }

    /**
     * 子类实现具体的生产环境配置校验逻辑。
     *
     * <p>Spring 容器初始化后由 {@link #validate()} 自动调用，
     * 子类应在此校验所有生产必填配置项，校验失败时抛出 {@link IllegalStateException}。</p>
     */
    protected abstract void doValidate();

    /**
     * 判断字符串是否为空或仅包含空白字符。
     *
     * @param value 待检查的字符串
     * @return {@code true} 当字符串为 null 或只包含空白字符时
     */
    protected static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 校验配置值非空，为空时抛出配置异常。
     *
     * @param name  配置项名称，用于构造异常消息
     * @param value 配置值
     * @throws IllegalStateException 当配置值为空时
     */
    protected void requireText(String name, String value) {
        if (isBlank(value)) throw invalid(name + " 未设置");
    }

    /**
     * 校验敏感配置非空且不是已知默认占位值（如 "minioadmin"、"admin"、"password" 等）。
     *
     * @param name  配置项名称，用于构造异常消息
     * @param value 配置值
     * @throws IllegalStateException 当配置值为空或使用默认占位凭据时
     */
    protected void requireSecret(String name, String value) {
        requireText(name, value);
        if (PLACEHOLDER_VALUES.contains(value.trim().toLowerCase(Locale.ROOT))) throw invalid(name + " 不得使用默认或占位凭据");
    }

    /**
     * 校验服务地址为完整 HTTP(S) URL，避免生产环境误配无效地址。
     *
     * @param name  配置项名称，用于构造异常消息
     * @param value 服务地址 URL
     * @throws IllegalStateException 当 URL 为空或不是有效的 HTTP/HTTPS 地址时
     */
    protected void validateHttpUrl(String name, String value) {
        requireText(name, value);
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)))
                throw invalid(name + " 必须是有效的 HTTP(S) 地址");
        } catch (IllegalArgumentException e) {
            throw invalid(name + " 必须是有效的 HTTP(S) 地址", e);
        }
    }

    /**
     * 创建带统一前缀的配置异常。
     *
     * @param reason 异常原因描述
     * @return 带统一前缀的 {@link IllegalStateException} 实例
     */
    protected IllegalStateException invalid(String reason) {
        return new IllegalStateException(prefix() + reason);
    }

    /**
     * 创建带统一前缀及底层原因链的配置异常。
     *
     * @param reason 异常原因描述
     * @param cause  底层异常
     * @return 带统一前缀和 cause 的 {@link IllegalStateException} 实例
     */
    protected IllegalStateException invalid(String reason, Throwable cause) {
        return new IllegalStateException(prefix() + reason, cause);
    }

    /**
     * 子类可覆写此方法以自定义异常消息前缀。
     *
     * @return 异常消息前缀，默认为"生产环境配置错误："
     */
    protected String prefix() {
        return "生产环境配置错误：";
    }
}
