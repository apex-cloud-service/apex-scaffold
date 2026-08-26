package com.apex.scaffold.interfaces.filter;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 扫描流量拦截配置属性类。
 *
 * <p>用于配置 {@link ScanTrafficGuardWebFilter} 的相关参数，包括拦截开关、返回状态码、
 * CONNECT 隧道拦截开关与扫描路径模式列表。
 *
 * <h2>配置前缀</h2>
 * <p>在 {@code application.yml} 中使用 {@code apex.security.scan-guard} 前缀进行配置：
 * <pre>{@code * apex:
 *   security:
 *     scan-guard:
 *       enabled: true
 *       block-status: 403
 *       block-connect-method: true
 *       path-patterns:
 *         - /HNAP1
 *         - /.git
 * }</pre>
 *
 * <h2>路径模式匹配语义</h2>
 * <ul>
 *   <li>不含通配符 {@code *} 的模式按路径前缀匹配（大小写不敏感），
 *       如 {@code /.git} 命中 {@code /.git/HEAD}、{@code /.gitignore}</li>
 *   <li>含 {@code *} 的模式按 glob 匹配（{@code *} 匹配任意字符，含路径分隔符），
 *       如 {@code *}.env* 命中 /app/.env 等任意层级下的环境变量文件</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ScanTrafficGuardWebFilter
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "apex.security.scan-guard")
public class ScanTrafficGuardProperties {

    /**
     * 是否启用扫描流量拦截。
     *
     * <p>关闭后过滤器不注册，WebFlux 过滤器链恢复原有行为。默认值为 true。
     */
    private boolean enabled = true;

    /**
     * 拦截响应的 HTTP 状态码。
     *
     * <p>403 明确拒绝（默认）；可改为 404，让扫描器误判路径不存在，
     * 降低其继续探测端口与服务存在的意愿。
     */
    private int blockStatus = 403;

    /**
     * 是否拦截 CONNECT 隧道探测请求。
     *
     * <p>扫描器常以 {@code CONNECT host:port HTTP/1.1} 探测服务器是否可作代理隧道。
     * 默认值为 true。
     */
    private boolean blockConnectMethod = true;

    /**
     * 扫描/漏洞探测路径模式列表。
     *
     * <p>匹配语义见类注释。默认列表覆盖公网扫描器高频探测路径，
     * 均与业务路径（{@code /api}、{@code /actuator}、{@code /health} 等）无交集。
     */
    private List<String> pathPatterns = List.of(
            "/HNAP1",
            "/CFIDE",
            "/.git",
            "/.env",
            "/dana-cached",
            "/success.txt",
            "/confluence",
            "/devicedesc.xml",
            "/SDK",
            "/cgi-bin",
            "/boaform",
            "/GponForm",
            "/autodiscover",
            "/remote",
            "/owa",
            "/ecp",
            "/phpmyadmin",
            "/_ignition",
            "/WEB-INF",
            "/server-status",
            "/server-info",
            "/.aws",
            "/actuator/env",
            "/manager",
            "/host-manager");
}
