package com.apex.scaffold.gateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 网关模块架构约束测试类。
 *
 * <p>使用 ArchUnit 校验 {@code apex-gateway} 包内部的分层与依赖方向，
 * 确保网关作为独立的 WebFlux 入口模块保持清晰的职责边界：
 * <ul>
 *   <li>网关不得依赖主应用的分层（interfaces / application / domain / infrastructure）</li>
 *   <li>网关不得引入阻塞式 Servlet 栈（javax.servlet / jakarta.servlet）</li>
 *   <li>过滤器层（filter）不得反向依赖配置层（config）与启动类</li>
 *   <li>安全验签层（security）不得依赖过滤器层（filter）</li>
 *   <li>过滤器命名需以 Filter 结尾，便于链路顺序维护</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@AnalyzeClasses(packages = "com.apex.scaffold.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
class GatewayArchTest {

    /**
     * 网关不得依赖主应用的业务分层。
     *
     * <p>网关仅承载路由、鉴权、限流、灰度等横切能力，业务逻辑应由下游微服务承担，
     * 因此不应出现对 interfaces / application / domain / infrastructure 包的依赖。
     */
    @ArchTest
    static final ArchRule gateway_does_not_depend_on_business_layers = noClasses()
            .that()
            .resideInAPackage("com.apex.scaffold.gateway..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.apex.scaffold..interfaces..",
                    "com.apex.scaffold..application..",
                    "com.apex.scaffold..domain..",
                    "com.apex.scaffold..infrastructure..");

    /**
     * 网关不得引入阻塞式 Servlet 栈。
     *
     * <p>网关基于 Spring Cloud Gateway（WebFlux 响应式栈），引入 Servlet API 会导致
     * 线程模型冲突与阻塞风险，必须彻底隔离。
     */
    @ArchTest
    static final ArchRule gateway_does_not_depend_on_servlet_api = noClasses()
            .that()
            .resideInAPackage("com.apex.scaffold.gateway..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("javax.servlet..", "jakarta.servlet..");

    /**
     * 过滤器层不得反向依赖配置层与启动类。
     *
     * <p>过滤器通过依赖注入获取 {@code security} / 基础设施 Bean，依赖方向应为
     * {@code config -> filter}、{@code GatewayApplication -> filter}，过滤器自身保持低耦合。
     */
    @ArchTest
    static final ArchRule filters_do_not_depend_on_config_or_application = noClasses()
            .that()
            .resideInAPackage("com.apex.scaffold.gateway.filter..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.apex.scaffold.gateway.config..", "com.apex.scaffold.gateway.GatewayApplication");

    /**
     * 安全验签层不得依赖过滤器层。
     *
     * <p>{@code security} 包提供纯验签能力（JWKS 解析 / 签名校验），
     * 由过滤器调用，反向依赖会破坏能力复用与可测试性。
     */
    @ArchTest
    static final ArchRule security_does_not_depend_on_filters = noClasses()
            .that()
            .resideInAPackage("com.apex.scaffold.gateway.security..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.apex.scaffold.gateway.filter..");
}
