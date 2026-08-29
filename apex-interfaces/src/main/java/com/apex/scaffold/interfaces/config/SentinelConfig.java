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
 * <h2>规则体系</h2>
 * <table>
 *   <caption>Sentinel 规则类型与用途</caption>
 *   <tr><th>规则类型</th><th>Sentinel 管理器</th><th>触发场景</th><th>触发后行为</th></tr>
 *   <tr><td>流控规则</td><td>{@link FlowRuleManager}</td><td>QPS 超过阈值</td><td>返回 429 Too Many Requests</td></tr>
 *   <tr><td>熔断降级规则</td><td>{@link DegradeRuleManager}</td><td>异常比例或慢调用比例超过阈值</td><td>返回 503 Service Unavailable</td></tr>
 *   <tr><td>系统保护规则</td><td>{@link SystemRuleManager}</td><td>CPU/线程/QPS 过载</td><td>返回 503 Service Unavailable</td></tr>
 * </table>
 *
 * <h2>限流策略分组</h2>
 * <table>
 *   <caption>流控规则分组与 QPS 阈值</caption>
 *   <tr><th>分组</th><th>接口数量</th><th>典型 QPS</th><th>覆盖范围</th></tr>
 *   <tr><td>认证（auth）</td><td>5</td><td>20~200</td><td>登录/刷新/短信</td></tr>
 *   <tr><td>文件（file）</td><td>4</td><td>10~200</td><td>上传/面部照片</td></tr>
 *   <tr><td>用户（user）</td><td>3</td><td>50~300</td><td>信息/列表</td></tr>
 *   <tr><td>媒体（media）</td><td>6</td><td>30~60</td><td>样本/收藏/共鸣/对战/投票</td></tr>
 *   <tr><td>排名（ranking）</td><td>3</td><td>100</td><td>排行榜</td></tr>
 *   <tr><td>同频（same-frequency）</td><td>7</td><td>30~100</td><td>状态/外观/兴趣/匿名会话</td></tr>
 *   <tr><td>其他（other）</td><td>5</td><td>20~100</td><td>伴侣/黑名单/举报/资料</td></tr>
 *   <tr><td>V1 探索投票（explore-v1）</td><td>6</td><td>40~100</td><td>获取对局/提交/跳过/共鸣/金睛火眼</td></tr>
 * </table>
 *
 * <h2>熔断参数说明</h2>
 * <ul>
 *   <li>异常比例阈值（count）：窗口内异常数占比超过此值触发熔断，统计窗口使用 Sentinel 默认值</li>
 *   <li>慢调用规则显式使用 30 秒统计窗口，{@code count} 表示 RT 阈值，{@code slowRatioThreshold} 表示慢调用比例</li>
 *   <li>熔断时长（timeWindow）：熔断打开后持续多少秒再进入半开探测</li>
 *   <li>认证接口（login/refresh）为 10s，文件/媒体/外部为 15~30s</li>
 * </ul>
 *
 * <h2>系统保护规则</h2>
 * <p>系统保护规则（CPU 使用率、入口 QPS）通过 {@link #initSystemRules()} 在启动时
 * 根据 {@code apex.sentinel.system-rules.*} 配置注册到 {@link SystemRuleManager}。
 * 可通过配置项开关与阈值，实现部署时调整；生产环境建议开启作为最后防线。</p>
 *
 * <h2>Spring 6.2 兼容性</h2>
 * <p>Sentinel 1.8.8 默认的 {@link BlockRequestHandler} 实现依赖 Spring 6.1.x 内部 API，
 * 在 Spring Boot 3.5（基于 Spring 6.2）中会因方法签名移除而导致运行时错误。
 * 本类通过自定义 {@code blockRequestHandler()} Bean 覆盖默认实现，确保兼容。</p>
 *
 * <h2>异常处理</h2>
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

    /** AI 分析慢调用判定阈值（毫秒），默认 30 秒，与远程分析服务读超时同量级，避免正常上传流量被误熔断。 */
    private final long aiAnalysisSlowCallThresholdMs;

    /** 投票探索接口慢调用判定阈值（毫秒），默认 3 秒，避免 DB 波动或压测场景被误熔断。 */
    private final long exploreSlowCallThresholdMs;

    /** 国际化消息解析工具，用于为限流/熔断响应生成多语言提示。 */
    private final MessageUtils messageUtils;

    /**
     * 构造方法，通过构造器注入全部配置项与依赖。
     *
     * <p>配置项均通过 {@code @Value} 注入，支持环境变量覆盖；AI 分析与投票探索慢调用阈值独立配置，
     * 便于按线上实际耗时校准，避免硬编码魔法数字。</p>
     *
     * @param systemRulesEnabled 是否启用系统保护规则
     * @param cpuUsageThreshold 系统保护 CPU 使用率阈值
     * @param entryQpsThreshold 系统保护入口 QPS 阈值
     * @param aiAnalysisSlowCallThresholdMs AI 分析慢调用判定阈值（毫秒）
     * @param exploreSlowCallThresholdMs 投票探索接口慢调用判定阈值（毫秒）
     * @param messageUtils 国际化消息解析工具
     */
    public SentinelConfig(
            @Value("${apex.sentinel.system-rules.enabled:true}") boolean systemRulesEnabled,
            @Value("${apex.sentinel.system-rules.cpu-usage-threshold:0.8}") double cpuUsageThreshold,
            @Value("${apex.sentinel.system-rules.entry-qps-threshold:2000}") double entryQpsThreshold,
            @Value("${apex.sentinel.degrade.slow-call.ai-analysis-threshold-ms:30000}")
                    long aiAnalysisSlowCallThresholdMs,
            @Value("${apex.sentinel.degrade.slow-call.explore-threshold-ms:3000}") long exploreSlowCallThresholdMs,
            MessageUtils messageUtils) {
        this.systemRulesEnabled = systemRulesEnabled;
        this.cpuUsageThreshold = cpuUsageThreshold;
        this.entryQpsThreshold = entryQpsThreshold;
        this.aiAnalysisSlowCallThresholdMs = aiAnalysisSlowCallThresholdMs;
        this.exploreSlowCallThresholdMs = exploreSlowCallThresholdMs;
        this.messageUtils = messageUtils;
    }

    /**
     * Excludes health probes from Sentinel traffic governance.
     *
     * <p>Liveness and readiness are control-plane signals. Applying global CPU protection to these
     * paths makes an otherwise healthy process report HTTP 503 precisely when operators need the
     * probes. Business API paths continue to use the standard Sentinel WebFlux resource cleaner.
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
     * <h3>响应格式</h3>
     * <p>返回全站统一 {@link Result} 结构（code/msg/data/traceId），与 IP 限流、
     * 全局异常处理器的响应格式保持一致，避免客户端需要兼容多套错误体。
     * 消息通过 {@link MessageUtils} 按请求 {@code Accept-Language} 国际化。</p>
     *
     * <h3>响应码规则</h3>
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
     * <p>在应用启动时注册 43 条流控规则到 {@link FlowRuleManager}。
     * 覆盖认证、文件、用户、媒体、排名、同频、通用、V1 探索投票和 V2 投票探索共 9 个业务模块。
     * 规则基于 QPS 维度，对所有调用来源生效（{@code limitApp=default}）。</p>
     */
    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>(43);

        addAuthRules(rules);
        addFileRules(rules);
        addUserRules(rules);
        addMediaRules(rules);
        addRankingRules(rules);
        addSameFrequencyRules(rules);
        addOtherRules(rules);
        addExploreV1Rules(rules);
        addExploreV2Rules(rules);

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
     * 文件模块限流规则。
     *
     * <p>上传 50 QPS、面部照片 10 QPS（AI 分析密集型）、生活照 10 QPS、CDN URL 200 QPS。</p>
     *
     * @param rules 限流规则列表
     */
    private void addFileRules(List<FlowRule> rules) {
        rules.add(createFlowRule("/api/v1/file/upload", 50));
        rules.add(createFlowRule("/api/v1/onboarding/face-profile", 10));
        rules.add(createFlowRule("/api/v1/photos/lifestyle", 10));
        rules.add(createFlowRule("/api/v1/file/url", 200));
    }

    /**
     * 用户模块限流规则。
     *
     * <p>信息查询 300 QPS（高频读）、单用户查询 200 QPS、列表 50 QPS。</p>
     *
     * @param rules 限流规则列表
     */
    private void addUserRules(List<FlowRule> rules) {
        rules.add(createFlowRule("/api/v1/user/info", 300));
        rules.add(createFlowRule("/api/v1/user/{userId}", 200));
        rules.add(createFlowRule("/api/v1/user/list", 50));
    }

    /**
     * 媒体模块限流规则。
     *
     * <p>样本查询 50 QPS、发布 30 QPS、收藏/共鸣 60 QPS、对战/投票 40 QPS。
     * 写操作（发布/对战/投票）阈值低于读操作（查询/收藏/共鸣）。</p>
     *
     * @param rules 限流规则列表
     */
    private void addMediaRules(List<FlowRule> rules) {
        rules.add(createFlowRule("/api/v1/media/samples", 50));
        rules.add(createFlowRule("/api/v1/media/samples/{sampleId}/publish", 30));
        rules.add(createFlowRule("/api/v1/media/photos/{photoId}/favorite", 60));
        rules.add(createFlowRule("/api/v1/media/photos/{photoId}/resonate", 60));
        rules.add(createFlowRule("/api/v1/media/photos/{photoId}/battle", 40));
        rules.add(createFlowRule("/api/v1/media/photos/{photoId}/vote", 40));
    }

    /**
     * 排名模块限流规则。
     *
     * <p>所有排名查询接口统一 100 QPS。</p>
     *
     * @param rules 限流规则列表
     */
    private void addRankingRules(List<FlowRule> rules) {
        rules.add(createFlowRule("/api/v1/ranking/list", 100));
        rules.add(createFlowRule("/api/v1/ranking/photo", 100));
        rules.add(createFlowRule("/api/v1/ranking/user", 100));
    }

    /**
     * 同频模块限流规则。
     *
     * <p>状态/外观/兴趣三种信息流接口统一 100 QPS。</p>
     *
     * @param rules 限流规则列表
     */
    private void addSameFrequencyRules(List<FlowRule> rules) {
        rules.add(createFlowRule("/api/v1/same-frequency/status", 100));
        rules.add(createFlowRule("/api/v1/same-frequency/appearance/feed", 100));
        rules.add(createFlowRule("/api/v1/same-frequency/interests/feed", 100));
        rules.add(createFlowRule("/api/v1/interest-universe", 100));
        rules.add(createFlowRule("/api/v1/interest-channels/home", 80));
        rules.add(createFlowRule("/api/v1/interest-connections/instant", 30));
        rules.add(createFlowRule("/api/v1/anonymous-interest-sessions/{sessionId}/messages", 80));
    }

    /**
     * 其他模块限流规则。
     *
     * <p>伴侣偏好 30 QPS、黑名单/举报 20 QPS（管理操作低频）、个人资料 100 QPS、引导 50 QPS。</p>
     *
     * @param rules 限流规则列表
     */
    private void addOtherRules(List<FlowRule> rules) {
        rules.add(createFlowRule("/api/v1/future-partner/preferences", 30));
        rules.add(createFlowRule("/api/v1/moderation/blacklist", 20));
        rules.add(createFlowRule("/api/v1/moderation/reports", 20));
        rules.add(createFlowRule("/api/v1/profile/me", 100));
        rules.add(createFlowRule("/api/v1/onboarding/guide/answers", 50));
    }

    /**
     * V2 投票探索模块限流规则。
     *
     * <p>二选一/四选二的 current（获取对局）和 prefetch（预取下一局）是投票主链路的核心读接口。
     * current 为高频读操作（100 QPS），prefetch 触发组局算法算力消耗较高（60 QPS）。
     * 阈值低于纯读操作（如 ranking 100 QPS），因为这些接口会触发 DB 写入（RESERVED 槽位、展示统计）。</p>
     *
     * @param rules 限流规则列表
     */
    private void addExploreV2Rules(List<FlowRule> rules) {
        rules.add(createFlowRule("/api/v2/explore/choice-2/current", 100));
        rules.add(createFlowRule("/api/v2/explore/choice-2/prefetch", 60));
        rules.add(createFlowRule("/api/v2/explore/four-pick-two/current", 100));
        rules.add(createFlowRule("/api/v2/explore/four-pick-two/prefetch", 60));
    }

    /**
     * V1 探索投票模块限流规则。
     *
     * <p>V1 探索投票端点是投票主链路的核心接口。按接口特性分配 QPS：
     * <ul>
     *   <li>获取对局（current、golden-eye/current）：高频读操作，100 / 80 QPS</li>
     *   <li>投票提交（submit、resonance/submit、golden-eye/verify）：写操作涉及 ELO 结算与事务，
     *       阈值低于读操作，60 / 40 QPS</li>
     *   <li>跳过对局（skip）：轻量写操作，40 QPS</li>
     * </ul>
     *
     * @param rules 限流规则列表
     */
    private void addExploreV1Rules(List<FlowRule> rules) {
        rules.add(createFlowRule("/api/v1/explore/battles/current", 100));
        rules.add(createFlowRule("/api/v1/explore/battles/{battleId}/submit", 60));
        rules.add(createFlowRule("/api/v1/explore/battles/{battleId}/skip", 40));
        rules.add(createFlowRule("/api/v1/explore/resonance/{battleId}/submit", 40));
        rules.add(createFlowRule("/api/v1/explore/golden-eye/current", 80));
        rules.add(createFlowRule("/api/v1/explore/golden-eye/{battleId}/verify", 40));
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
     * 创建慢调用比例熔断降级规则。
     *
     * <p>基于慢调用比例（slow request ratio）触发熔断。
     * 当统计窗口内响应时间超过 {@code maxAllowedRt} 毫秒的请求占比超过 {@code count} 时，
     * 该资源进入熔断 OPEN 状态。适用于 AI 分析等耗时波动的接口，
     * 即使接口不报错但大面积变慢时也能自动降级保护。
     *
     * <p>Sentinel {@code DEGRADE_GRADE_RT} 模式下：
     * {@code count} 字段承载 RT 阈值（毫秒），{@code slowRatioThreshold} 承载慢调用比例阈值。
     * 本方法将参数名与 Sentinel 内部字段语义对齐，避免混淆。</p>
     *
     * @param resource     资源名称（接口路径）
     * @param count        慢调用比例阈值（0.0 ~ 1.0），如 0.5 表示 50%
     * @param timeWindow   熔断时长（秒）
     * @param maxAllowedRt 慢调用判定阈值（毫秒），超过此 RT 的请求视为慢调用
     * @return 配置完成的 {@link DegradeRule} 实例
     */
    private DegradeRule createSlowCallDegradeRule(String resource, double count, int timeWindow, int maxAllowedRt) {
        DegradeRule rule = new DegradeRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        rule.setCount(maxAllowedRt);
        rule.setSlowRatioThreshold(count);
        rule.setTimeWindow(timeWindow);
        rule.setMinRequestAmount(5);
        rule.setStatIntervalMs(30000);
        return rule;
    }

    /**
     * 初始化全局熔断降级规则。
     *
     * <p>在应用启动时注册异常比例 + 慢调用比例两类熔断规则到 {@link DegradeRuleManager}。
     * 覆盖认证、文件、媒体、外部依赖、V1 探索投票共 5 个模块。</p>
     */
    @PostConstruct
    public void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>(30);

        addAuthDegradeRules(rules);
        addFileDegradeRules(rules);
        addMediaDegradeRules(rules);
        addExternalDegradeRules(rules);
        addExploreV1DegradeRules(rules);
        addSlowCallDegradeRules(rules);

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
     * 文件模块熔断降级规则。
     *
     * <p>上传 50% 异常率熔断 15s、面部照片/生活照 50% 异常率熔断 20s（AI 分析耗时较长）、
     * CDN URL 30% 异常率熔断 10s。</p>
     *
     * @param rules 熔断降级规则列表
     */
    private void addFileDegradeRules(List<DegradeRule> rules) {
        rules.add(createDegradeRule("/api/v1/file/upload", 0.5, 15));
        rules.add(createDegradeRule("/api/v1/onboarding/face-profile", 0.5, 20));
        rules.add(createDegradeRule("/api/v1/photos/lifestyle", 0.5, 20));
        rules.add(createDegradeRule("/api/v1/file/url", 0.3, 10));
    }

    /**
     * 媒体模块熔断降级规则。
     *
     * <p>样本查询 50% 异常率熔断 15s、发布 50% 异常率熔断 20s、
     * 对战/投票 50% 异常率熔断 10s（对延迟敏感，快速熔断快恢复）。</p>
     *
     * @param rules 熔断降级规则列表
     */
    private void addMediaDegradeRules(List<DegradeRule> rules) {
        rules.add(createDegradeRule("/api/v1/media/samples", 0.5, 15));
        rules.add(createDegradeRule("/api/v1/media/samples/{sampleId}/publish", 0.5, 20));
        rules.add(createDegradeRule("/api/v1/media/photos/{photoId}/battle", 0.5, 10));
        rules.add(createDegradeRule("/api/v1/media/photos/{photoId}/vote", 0.5, 10));
    }

    /**
     * 外部依赖熔断降级规则。
     *
     * <p>微信 OAuth 50% 异常率熔断 30s、微信支付 30% 异常率熔断 30s（第三方依赖恢复较慢）、
     * 短信发送 30% 异常率熔断 20s、对象存储上传 50% 异常率熔断 15s。</p>
     *
     * @param rules 熔断降级规则列表
     */
    private void addExternalDegradeRules(List<DegradeRule> rules) {
        rules.add(createDegradeRule("/api/v1/wechat/oauth", 0.5, 30));
        rules.add(createDegradeRule("/api/v1/wechat/pay", 0.3, 30));
        rules.add(createDegradeRule("/api/v1/sms/send", 0.3, 20));
        rules.add(createDegradeRule("/api/v1/oss/upload", 0.5, 15));
    }

    /**
     * V1 探索投票模块异常比例熔断降级规则。
     *
     * <p>对 V1 探索投票核心接口设置异常比例熔断保护：
     * <ul>
     *   <li>获取对局（current）— 50% 异常率熔断 15s，因涉及阻塞式 DB 查询与组局算法</li>
     *   <li>投票提交（submit、resonance/submit）— 50% 异常率熔断 15s，因涉及 ELO 结算事务</li>
     *   <li>跳过对局（skip）— 50% 异常率熔断 10s，轻量操作快速恢复</li>
     * </ul>
     *
     * @param rules 熔断降级规则列表
     */
    private void addExploreV1DegradeRules(List<DegradeRule> rules) {
        rules.add(createDegradeRule("/api/v1/explore/battles/current", 0.5, 15));
        rules.add(createDegradeRule("/api/v1/explore/battles/{battleId}/submit", 0.5, 15));
        rules.add(createDegradeRule("/api/v1/explore/battles/{battleId}/skip", 0.5, 10));
        rules.add(createDegradeRule("/api/v1/explore/resonance/{battleId}/submit", 0.5, 15));
    }

    /**
     * 慢调用比例熔断降级规则。
     *
     * <p>针对 AI 分析等耗时波动接口，即使不报错但大面积变慢时也能自动降级：
     * <ul>
     *   <li>面部分析（3000ms）— 50% 慢调用率触发熔断 30s</li>
     *   <li>生活照分析（3000ms）— 50% 慢调用率触发熔断 30s</li>
     *   <li>对战提交（2000ms）— 50% 慢调用率触发熔断 15s</li>
     *   <li>投票提交（2000ms）— 50% 慢调用率触发熔断 15s</li>
     *   <li>微信 OAuth（3000ms）— 50% 慢调用率触发熔断 30s</li>
     *   <li>对象存储上传（5000ms）— 50% 慢调用率触发熔断 20s</li>
     *   <li>V1 获取对局（3000ms）— 50% 慢调用率触发熔断 15s，涉及阻塞式 DB 查询与组局算法</li>
     *   <li>V1 投票提交（2000ms）— 50% 慢调用率触发熔断 15s，涉及 ELO 结算事务</li>
     * </ul>
     *
     * @param rules 熔断降级规则列表
     */
    private void addSlowCallDegradeRules(List<DegradeRule> rules) {
        // AI 分析密集型接口：慢调用阈值由 apex.sentinel.degrade.slow-call.ai-analysis-threshold-ms 配置，
        // 默认 30s，与远程分析服务读超时同量级，避免正常上传流量（图片上传+审核+Python 校验）被批量误熔断。
        rules.add(createSlowCallDegradeRule(
                "/api/v1/onboarding/face-profile", 0.5, 30, (int) aiAnalysisSlowCallThresholdMs));
        rules.add(createSlowCallDegradeRule("/api/v1/photos/lifestyle", 0.5, 30, (int) aiAnalysisSlowCallThresholdMs));
        // 对延迟敏感的业务接口
        rules.add(createSlowCallDegradeRule("/api/v1/media/photos/{photoId}/battle", 0.5, 15, 2000));
        rules.add(createSlowCallDegradeRule("/api/v1/media/photos/{photoId}/vote", 0.5, 15, 2000));
        // 外部依赖接口：第三方恢复较慢，熔断窗口适当延长
        rules.add(createSlowCallDegradeRule("/api/v1/wechat/oauth", 0.5, 30, 3000));
        rules.add(createSlowCallDegradeRule("/api/v1/oss/upload", 0.5, 20, 5000));
        // V2 投票探索接口：current 涉及阻塞式数据库查询与组局算法，
        // 慢调用阈值由 apex.sentinel.degrade.slow-call.explore-threshold-ms 配置（默认 3s），
        // 允许按真实环境耗时校准，避免压测或 DB 波动导致误熔断。
        rules.add(createSlowCallDegradeRule(
                "/api/v2/explore/choice-2/current", 0.5, 15, (int) exploreSlowCallThresholdMs));
        rules.add(createSlowCallDegradeRule("/api/v2/explore/choice-2/prefetch", 0.5, 15, 2000));
        rules.add(createSlowCallDegradeRule(
                "/api/v2/explore/four-pick-two/current", 0.5, 15, (int) exploreSlowCallThresholdMs));
        rules.add(createSlowCallDegradeRule("/api/v2/explore/four-pick-two/prefetch", 0.5, 15, 2000));
        // V1 探索投票接口：current 涉及阻塞式数据库查询与组局算法，阈值与 V2 保持一致，可配置
        rules.add(createSlowCallDegradeRule(
                "/api/v1/explore/battles/current", 0.5, 15, (int) exploreSlowCallThresholdMs));
        // V1 投票提交：涉及 ELO 结算事务，2000ms 阈值与 V2 submit 对齐
        rules.add(createSlowCallDegradeRule("/api/v1/explore/battles/{battleId}/submit", 0.5, 15, 2000));
    }

    /**
     * 初始化系统保护规则。
     *
     * <p>受 {@code apex.sentinel.system-rules.enabled} 控制（默认 {@code true}）。
     * 本地开发环境建议设为 {@code false} 避免误拦；生产环境建议开启作为最后防线。</p>
     *
     * <h3>启用的规则</h3>
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
