package com.apex.scaffold.interfaces.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * API 版本管理配置类
 *
 * <p>配置 Spring WebFlux 的路径匹配规则,支持 API 版本控制和结尾斜杠兼容。
 * 本工程基于函数式路由（RouterFunction）实现 RESTful API,URL 路径版本控制
 * （如 {@code /api/v1/users}、{@code /api/v2/users}）由各路由模块直接在 RouterFunction 中注册。
 *
 * <h2>路径匹配规则</h2>
 * <ul>
 *   <li>结尾斜杠兼容：{@code /api/v1/users} 与 {@code /api/v1/users/} 视为同一路由</li>
 *   <li>路径变量匹配：支持 {@code /api/v1/users/{id}} 等 RESTful 路径模式</li>
 * </ul>
 *
 * <h2>版本控制策略</h2>
 * <p>采用 URL 路径版本控制方式,每个 API 版本独立注册路由：
 * <ul>
 *   <li>v1：{@code /api/v1/**} - 第一代 API</li>
 *   <li>v2：{@code /api/v2/**} - 第二代 API（向后兼容）</li>
 *   <li>admin-api：{@code /admin-api/v1/**} - 管理端 API</li>
 * </ul>
 *
 * <h2>与其他版本控制方式的对比</h2>
 * <table>
 *   <tr><th>方式</th><th>示例</th><th>优点</th><th>缺点</th></tr>
 *   <tr><td>URL 路径</td><td>/api/v1/users</td><td>清晰直观、易于缓存</td><td>URL 较长</td></tr>
 *   <tr><td>请求头</td><td>Accept: application/vnd.api.v1+json</td><td>URL 简洁</td><td>不利于浏览器直接访问</td></tr>
 *   <tr><td>查询参数</td><td>/api/users?version=1</td><td>灵活</td><td>语义不清晰</td></tr>
 * </table>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see org.springframework.web.reactive.config.WebFluxConfigurer
 * @see org.springframework.web.reactive.config.PathMatchConfigurer
 */
@Configuration
public class ApiVersionConfig implements WebFluxConfigurer {

    /**
     * 配置 API 路径匹配规则
     *
     * <p>启用结尾斜杠匹配模式,使 {@code /api/v1/users} 与 {@code /api/v1/users/}
     * 指向同一路由处理器,避免客户端因结尾斜杠差异导致 404 错误。
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>浏览器自动补全可能添加或移除结尾斜杠</li>
     *   <li>不同前端框架对 URL 规范化处理不一致</li>
     *   <li>手动输入 URL 时容易遗漏或多加斜杠</li>
     * </ul>
     *
     * @param configurer 路径匹配配置器
     */
    @Override
    public void configurePathMatching(PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(true);
    }
}
