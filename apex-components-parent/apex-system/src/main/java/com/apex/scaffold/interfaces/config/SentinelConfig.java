package com.apex.scaffold.interfaces.config;

import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.WebFluxCallbackManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.SentinelResourceUtil;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * Sentinel 流量治理配置类。
 *
 * <p>作为阿里云 Sentinel 在 WebFlux 响应式架构下的统一配置入口，
 * 集中管理限流（Flow Control）、熔断降级（Degrade）和系统保护（System Guard）三类规则。
 * 所有规则在应用启动时通过 {@link PostConstruct} 自动注册到 Sentinel 规则管理器。</p>
 *
 * <h2>设计说明</h2>
 * <table>
 *   <caption>Sentinel 规则类型与用途</caption>
 *   <tr><th>规则类型</th><th>Sentinel 管理器</th><th>触发场景</th><th>触发后行为</th></tr>
 *   <tr><td>流控规则</td><td>{@link FlowRuleManager}</td><td>QPS 超过阈值</td><td>返回 429 Too Many Requests</td></tr>
 *   <tr><td>熔断降级规则</td><td>{@link DegradeRuleManager}</td><td>异常比例超过阈值</td><td>返回 503 Service Unavailable</td></tr>
 *   <tr><td>系统保护规则</td><td>{@link SystemRuleManager}</td><td>CPU/线程/QPS 过载</td><td>返回 503 Service Unavailable</td></tr>
 * </table>
 *
 * <h2>设计说明</h2>
 * <table>
 *   <caption>流控规则分组与 QPS 阈值</caption>
 *   <tr><th>分组</th><th>接口数量</th><th>典型 QPS</th><th>覆盖范围</th></tr>
 *   <tr><td>认证（auth）</td><td>5</td><td>20~200</td><td>登录/刷新/短信</td></tr>
 *   <tr><td>用户（user）</td><td>1</td><td>200</td><td>/api/v1/user/**（info/list 请求经模板归一化并入 {userId} 规则）</td></tr>
 * </table>
 *
 * <p>BE-071（2026-11-28）：按逐条路由证据清理无路由注册的退役业务域规则
 * （文件/媒体/排名/同频/其他/V1 探索投票/V2 投票探索共 34 条，证据清单见
 * docs/records/2026-11-28-be071-sentinel-baseline-audit.md），仅保留 system 模块
 * 实际注册的认证与用户路径；BE-072 进一步移除被 {userId} 模板遮蔽的 user/info、user/list 两条；Nacos 动态规则仍可整体替换扩展。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>异常比例阈值（count）：窗口内异常数占比超过此值触发熔断，统计窗口使用 Sentinel 默认值</li>
 *   <li>熔断时长（timeWindow）：熔断打开后持续多少秒再进入半开探测</li>
 *   <li>认证接口（login/refresh）为 10s</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>系统保护规则（CPU 使用率、入口 QPS）通过 {@link #initSystemRules()} 在启动时
 * 根据 {@code apex.sentinel.system-rules.*} 配置注册到 {@link SystemRuleManager}。
 * 可通过配置项开关与阈值，实现部署时调整；生产环境建议开启作为最后防线。</p>
 *
 * <h2>设计说明</h2>
 * <p>Sentinel 1.8.8 默认的 {@link BlockRequestHandler} 实现依赖 Spring 6.1.x 内部 API，
 * 在 Spring Boot 3.5（基于 Spring 6.2）中会因方法签名移除而导致运行时错误。
 * 本类通过自定义 {@code blockRequestHandler()} Bean 覆盖默认实现，确保兼容。</p>
 *
 * <h2>设计说明</h2>
 * <p>限流/熔断触发的 Sentinel 异常由 {@link com.apex.scaffold.interfaces.exception.SentinelExceptionHandler}
 * 统一捕获并转换为国际化 HTTP 错误响应。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.interfaces.exception.SentinelExceptionHandler
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "apex.features", name = "monitoring", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(FlowRuleManager.class)
public class SentinelConfig {

    /** 支持的语言列表，与 {@code LocaleWebFilter} 保持一致，用于限流/熔断响应的国际化。 */
    private static final List<Locale> SUPPORTED_LOCALES =
            List.of(Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE, Locale.US, Locale.forLanguageTag("th-TH"));

    /** 是否启用系统保护规则，控制自动 CPU 使用率和入口 QPS 保护。 */
    private final boolean systemRulesEnabled;

    /** 系统保护 CPU 使用率阈值（0.0~1.0），超过此值触发降级。 */
    private final double cpuUsageThreshold;

    /** 系统保护入口 QPS 阈值，超过此值触发流控。 */
    private final double entryQpsThreshold;

    /** 国际化消息解析工具，用于为限流/熔断响应生成多语言提示。 */
    private final MessageUtils messageUtils;

    /**
     * 构造方法，通过构造器注入全部配置项与依赖。
     *
     * <p>配置项均通过 {@code @Value} 注入，支持环境变量覆盖。</p>
     *
     * @param systemRulesEnabled 是否启用系统保护规则
     * @param cpuUsageThreshold 系统保护 CPU 使用率阈值
     * @param entryQpsThreshold 系统保护入口 QPS 阈值
     * @param messageUtils 国际化消息解析工具
     */
    public SentinelConfig(
            @Value("${apex.sentinel.system-rules.enabled:true}") boolean systemRulesEnabled,
            @Value("${apex.sentinel.system-rules.cpu-usage-threshold:0.8}") double cpuUsageThreshold,
            @Value("${apex.sentinel.system-rules.entry-qps-threshold:2000}") double entryQpsThreshold,
            MessageUtils messageUtils) {
        this.systemRulesEnabled = systemRulesEnabled;
        this.cpuUsageThreshold = cpuUsageThreshold;
        this.entryQpsThreshold = entryQpsThreshold;
        this.messageUtils = messageUtils;
    }

    /**
     * 将健康探针排除在 Sentinel 流量治理之外。
     *
     * <p>存活与就绪探针属于控制面信号；若对这些路径施加全局 CPU 保护，本健康的进程会在
     * 运维最需要探针时返回 503。业务 API 路径仍走标准 Sentinel WebFlux 资源清理器。
     */
    @PostConstruct
    public void configureWebFluxCallbacks() {
        WebFluxCallbackManager.setUrlCleaner((exchange, originalUrl) -> cleanSentinelResource(originalUrl));
    }

    /**
     * 清理 Sentinel 资源名称（委托 {@link SentinelResourceUtil#cleanSentinelResource}）。
     *
     * <p>对健康检查相关路径返回 {@code null} 使其不受 Sentinel 流控规则约束；
     * 其余路径归一化含路径变量的资源名后返回。保留为包内静态方法，兼容现有测试调用。</p>
     *
     * @param originalUrl 原始请求 URL
     * @return 清理后的资源名称，健康检查路径返回 {@code null}
     */
    static String cleanSentinelResource(String originalUrl) {
        return SentinelResourceUtil.cleanSentinelResource(originalUrl);
    }

    /**
     * 将含路径变量的运行时 URL 归一化为 Sentinel 规则模板名（委托 {@link SentinelResourceUtil#normalizePathVariables}）。
     *
     * <p>与 {@code cleanSentinelResource} 保持一致，统一收敛到
     * {@link SentinelResourceUtil#PATH_VARIABLE_TEMPLATES}，避免两套归一化逻辑漂移。</p>
     *
     * @param rawUrl 请求原始路径
     * @return 归一化后的规则资源名；{@code rawUrl} 为 {@code null} 时返回 {@code null}
     */
    static String normalizePathVariables(String rawUrl) {
        return SentinelResourceUtil.normalizePathVariables(rawUrl);
    }

    /**
     * 从请求 {@code Accept-Language} 头解析最佳语言。
     *
     * <p>限流/熔断响应需要国际化提示，但 {@link BlockRequestHandler} 是同步回调，
     * 无法依赖 Reactor Context 中的 Locale。此处直接从请求头解析，逻辑与
     * {@code LocaleWebFilter} 保持一致；解析失败或缺少请求头时回退简体中文。</p>
     *
     * @param exchange 当前 WebFlux 服务端交换对象
     * @return 解析出的语言；无法匹配时返回 {@link Locale#SIMPLIFIED_CHINESE}
     */
    private static Locale resolveLocale(ServerWebExchange exchange) {
        String acceptLanguage = exchange.getRequest().getHeaders().getFirst("Accept-Language");
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        try {
            List<Locale.LanguageRange> languageRanges = Locale.LanguageRange.parse(acceptLanguage);
            return Locale.lookup(languageRanges, SUPPORTED_LOCALES);
        } catch (Exception e) {
            log.debug("Accept-Language 解析失败，使用默认语言: {}", acceptLanguage, e);
            return Locale.SIMPLIFIED_CHINESE;
        }
    }

    /**
     * 自定义 Sentinel WebFlux 阻塞响应处理器（Spring 6.2 兼容）。
     *
     * <p>Sentinel 1.8.8 默认 {@code WebFluxBlockHandler} 实现调用了
     * Spring 6.1.x 中已标记为过时、6.2.x 中已物理移除的方法
     * （如 {@code DefaultServerResponseBuilder.syncBody(Object)}），
     * 导致运行时 {@code NoSuchMethodError}。本 Bean 提供兼容实现替代默认处理器。</p>
     *
     * <p><strong>响应格式</strong></p>
     * <p>返回全站统一 {@link Result} 结构（code/msg/data/traceId），与 IP 限流、
     * 全局异常处理器的响应格式保持一致，避免客户端需要兼容多套错误体。
     * 消息通过 {@link MessageUtils} 按请求 {@code Accept-Language} 国际化。</p>
     *
     * <p><strong>响应码规则</strong></p>
     * <ul>
     *   <li>限流异常（{@link FlowException} / {@link ParamFlowException}）→ HTTP 429，
     *       业务码 {@link ResultCode#RATE_LIMIT}（2001）</li>
     *   <li>熔断降级异常（{@link DegradeException}）→ HTTP 503，
     *       业务码 {@link ResultCode#DEGRADE}（2003）</li>
     *   <li>其他 Sentinel 阻塞异常（系统保护等）→ HTTP 503，
     *       业务码 {@link ResultCode#CIRCUIT_BREAKER}（2002）</li>
     * </ul>
     *
     * @return Sentinel WebFlux 阻塞响应处理器
     */
    @Bean
    public BlockRequestHandler blockRequestHandler() {
        return (exchange, throwable) -> {
            boolean rateLimited = throwable instanceof FlowException || throwable instanceof ParamFlowException;
            boolean degraded = throwable instanceof DegradeException;
            HttpStatus status = rateLimited ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE;
            ResultCode resultCode =
                    rateLimited ? ResultCode.RATE_LIMIT : (degraded ? ResultCode.DEGRADE : ResultCode.CIRCUIT_BREAKER);
            String message = messageUtils.get(resolveLocale(exchange), resultCode.getMessageKey());
            Result<?> result = Result.error(resultCode.getCode(), message, TraceIdUtil.getTraceId());
            return ServerResponse.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(result);
        };
    }

    /**
     * 创建标准 QPS 限流规则。
     *
     * <p>所有接口均使用 QPS（每秒请求数）作为限流维度，
     * 适用应用类型为 {@code default}（对所有调用来源生效）。</p>
     *
     * @param resource 资源名称（接口路径，如 {@code /api/v1/auth/login}）
     * @param count    QPS 阈值，超过此值触发限流
     * @return 配置完成的 {@link FlowRule} 实例
     */
    private FlowRule createFlowRule(String resource, int count) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        rule.setLimitApp("default");
        return rule;
    }

    /**
     * 初始化全局限流规则。
     *
     * <p>在应用启动时注册 6 条流控规则到 {@link FlowRuleManager}（BE-071 清理退役业务域死规则、
     * BE-072 移除被 {userId} 模板遮蔽的 user/info、user/list 两条后）。
     * 覆盖认证与用户两个业务模块。规则基于 QPS 维度，对所有调用来源生效（{@code limitApp=default}）。</p>
     */
    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>(6);

        addAuthRules(rules);
        addUserRules(rules);

        FlowRuleManager.loadRules(rules);
    }

    /**
     * 认证模块限流规则。
     *
     * <p>登录 50 QPS、刷新令牌 200 QPS、短信验证码 20 QPS（防刷）、登出 100 QPS。</p>
     *
     * @param rules 限流规则列表
     */
    private void addAuthRules(List<FlowRule> rules) {
        rules.add(createFlowRule("/api/v1/auth/login", 50));
        rules.add(createFlowRule("/api/v1/auth/refresh", 200));
        rules.add(createFlowRule("/api/v1/auth/sms/code", 20));
        rules.add(createFlowRule("/api/v1/auth/cl/sms/code", 20));
        rules.add(createFlowRule("/api/v1/auth/logout", 100));
    }

    /**
     * 用户模块限流规则。
     *
     * <p>信息查询 300 QPS（高频读）、单用户查询 200 QPS、列表 50 QPS。</p>
     *
     * @param rules 限流规则列表
     */
    private void addUserRules(List<FlowRule> rules) {
        // user/info、user/list 的请求经 PATH_VARIABLE_TEMPLATES 归一化为 /api/v1/user/{userId}
        // 资源（占位符通配语义），仅保留 {userId} 模板规则即可覆盖全部 /api/v1/user/** 请求
        // （BE-072：移除被模板遮蔽的 info/list 两条规则）。
        rules.add(createFlowRule("/api/v1/user/{userId}", 200));
    }

    /**
     * 创建异常比例熔断降级规则。
     *
     * <p>基于异常比例（exception ratio）触发熔断。
     * 当滑动窗口内异常请求占比超过 {@code count} 时，
     * 该资源进入熔断 OPEN 状态，持续 {@code timeWindow} 秒后进入半开探测。</p>
     *
     * @param resource   资源名称（接口路径）
     * @param count      异常比例阈值（0.0 ~ 1.0），如 0.5 表示 50% 异常率触发熔断
     * @param timeWindow 熔断时长（秒），熔断打开后保持的时间窗口
     * @return 配置完成的 {@link DegradeRule} 实例
     */
    private DegradeRule createDegradeRule(String resource, double count, int timeWindow) {
        DegradeRule rule = new DegradeRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        rule.setCount(count);
        rule.setTimeWindow(timeWindow);
        return rule;
    }

    /**
     * 初始化全局熔断降级规则。
     *
     * <p>在应用启动时注册异常比例熔断规则到 {@link DegradeRuleManager}
     * （BE-071 清理退役业务域死规则后，仅保留认证模块两条）。</p>
     */
    @PostConstruct
    public void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>(2);

        addAuthDegradeRules(rules);

        DegradeRuleManager.loadRules(rules);
    }

    /**
     * 认证模块熔断降级规则。
     *
     * <p>登录 50% 异常率熔断 10s、刷新令牌 30% 异常率熔断 10s。</p>
     *
     * @param rules 熔断降级规则列表
     */
    private void addAuthDegradeRules(List<DegradeRule> rules) {
        rules.add(createDegradeRule("/api/v1/auth/login", 0.5, 10));
        rules.add(createDegradeRule("/api/v1/auth/refresh", 0.3, 10));
    }

    /**
     * 初始化系统保护规则。
     *
     * <p>受 {@code apex.sentinel.system-rules.enabled} 控制（默认 {@code true}）。
     * 本地开发环境建议设为 {@code false} 避免误拦；生产环境建议开启作为最后防线。</p>
     *
     * <p><strong>启用的规则</strong></p>
     * <ul>
     *   <li>CPU 使用率阈值（可配置，默认 0.8）</li>
     *   <li>入口 QPS 阈值（可配置，默认 2000）</li>
     * </ul>
     *
     * <p>注：不再使用 {@code maxThread} 规则，因为 WebFlux/Netty 的线程模型下
     * 线程数远达不到 Servlet 容器的量级，该规则实际从不触发。</p>
     *
     * @see SystemRuleManager
     */
    @PostConstruct
    public void initSystemRules() {
        if (!systemRulesEnabled) {
            // 同一 JVM 内可能复用已加载过系统保护规则的 Spring 上下文（如集成测试
            // 中先启动非 it-local 上下文），仅 return 会残留 SystemRuleManager 中的旧规则，
            // 导致后续请求继续被 SystemBlockException（503）拦截。显式清空保证"关闭"语义真实生效。
            SystemRuleManager.loadRules(new ArrayList<>());
            log.info("系统保护规则已通过配置 apex.sentinel.system-rules.enabled=false 关闭");
            return;
        }
        List<SystemRule> rules = new ArrayList<>(2);

        SystemRule cpuRule = new SystemRule();
        cpuRule.setHighestCpuUsage(cpuUsageThreshold);
        rules.add(cpuRule);

        SystemRule qpsRule = new SystemRule();
        qpsRule.setQps(entryQpsThreshold);
        rules.add(qpsRule);

        SystemRuleManager.loadRules(rules);
        log.info("系统保护规则已启用: cpuUsage={}, entryQps={}", cpuUsageThreshold, entryQpsThreshold);
    }
}
