package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.application.service.OperationLogService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.util.LogSafetyUtil;
import com.apex.scaffold.common.util.RequestUtil;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 操作日志切面
 *
 * <p>
 * 拦截所有标注了 {@link OperationLog} 注解的方法，自动记录操作日志。
 * 支持响应式（{@link Mono}）和非响应式两种方法返回类型的日志记录。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>自动记录用户操作的模块、类型、描述、参数 IP 等信息</li>
 *   <li>对返回类型为 {@link Mono} 的方法，使用响应式的 {@code doOnSuccess} / {@code doOnError}
 *       在异步操作完成后记录日志，而非同步阻塞等待</li>
 *   <li>异步批量写入数据库，通过阻塞队列实现生产-消费模式</li>
 *   <li>支持日志批量合并，减少数据库写入次数</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>生产者-消费者模式：使用阻塞队列实现日志的异步批量写入</li>
 *   <li>装饰器模式：对 {@link Mono} 返回值添加 {@code doOnSuccess/doOnError} 回调</li>
 *   <li>策略模式：根据返回类型选择不同的日志记录策略</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see OperationLog
 * @see OperationLogEntity
 * @see OperationLogService
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    /** IP 地址相关常量 */
    private static final String IP_UNKNOWN = "unknown";

    private static final String IPV6_LOCALHOST_FULL = "0:0:0:0:0:0:0:1";
    private static final String IPV6_LOCALHOST_SHORT = "::1";
    private static final String IP_PREFIX_LOOPBACK = "127.";
    private static final String IP_PREFIX_PRIVATE_10 = "10.";
    private static final String IP_PREFIX_PRIVATE_192 = "192.168.";
    private static final String IP_PREFIX_PRIVATE_172 = "172.";
    private static final int IP_PRIVATE_172_RANGE_START = 16;
    private static final int IP_PRIVATE_172_RANGE_END = 31;
    private static final int IP_SPLIT_PARTS_THRESHOLD = 2;

    /** Jackson 序列化器，用于记录请求参数与响应体。 */
    private final ObjectMapper objectMapper;

    /** 操作日志服务，负责持久化操作日志记录。 */
    private final OperationLogService operationLogService;

    /** 用户服务，用于解析操作人信息。 */
    private final UserService userService;

    /** 是否启用操作日志记录，由 {@code operation-log.enabled} 配置控制。 */
    @Value("${operation-log.enabled:true}")
    private boolean operationLogEnabled;

    /** 是否记录请求参数内容，由 {@code operation-log.log-params} 配置控制。 */
    @Value("${operation-log.log-params:true}")
    private boolean logParams;

    /** 是否记录响应结果内容，由 {@code operation-log.log-result} 配置控制。 */
    @Value("${operation-log.log-result:true}")
    private boolean logResult;

    /** 请求参数最大截断长度，超过此长度的参数内容会被截断。 */
    @Value("${operation-log.max-param-length:2000}")
    private int maxParamLength;

    /** 响应结果最大截断长度，超过此长度的响应内容会被截断。 */
    @Value("${operation-log.max-result-length:2000}")
    private int maxResultLength;

    /**
     * 构造操作日志切面
     *
     * @param operationLogService 操作日志服务
     * @param userService         用户服务，用于解析操作人信息
     * @param objectMapper        Jackson ObjectMapper
     */
    public OperationLogAspect(
            OperationLogService operationLogService, UserService userService, ObjectMapper objectMapper) {
        this.operationLogService = operationLogService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /**
     * 拦截所有标注 {@link OperationLog} 注解的方法，记录操作日志。
     *
     * <p>该方法根据返回值类型采用不同的处理策略：
     * <ul>
     *   <li>若目标方法返回 {@link Mono}，则通过 {@code flatMap} 和 {@code doOnError} 异步记录日志，
     *       不阻塞当前事件循环线程</li>
     *   <li>若目标方法返回其他类型（同步），则在 {@code try-catch-finally} 中同步记录日志</li>
     * </ul>
     *
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 执行异常
     */
    @Around("@annotation(com.apex.scaffold.common.annotation.OperationLog)")
    public Object aroundOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        java.lang.reflect.Method method = org.springframework.aop.support.AopUtils.getMostSpecificMethod(
                signature.getMethod(), joinPoint.getTarget().getClass());
        OperationLog operationLog = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                method, OperationLog.class);
        return around(joinPoint, operationLog);
    }

    /**
     * 操作日志切面主体：按目标方法返回值类型分派同步或响应式记录逻辑。
     *
     * <p>由 {@link #aroundOperation} 解析出注解后调用；{@code operationLogEnabled} 为 {@code false}
     * 时直接放行，不做任何日志采集。
     *
     * @param joinPoint 连接点
     * @param operationLog 目标方法上的操作日志注解（已合并元注解）
     * @return 目标方法返回值，可能已被包装以在信号完成/出错时落库
     * @throws Throwable 目标方法执行异常
     */
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        if (!operationLogEnabled) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        String traceId = TraceIdUtil.getTraceId();

        // C3 修复：在切面入口捕获当时的 tenantId，后续 enrichUserInfo 异步切线程后
        // ThreadLocal 会丢失；传参到 saveAsync，在 DB 写入线程显式安装/恢复，
        // 保证 Repository 通过 TenantLineInnerInterceptor 能读到正确租户。
        String tenantIdAtEntry = TenantContext.currentTenantId().orElse(null);

        if (isMonoReturnType(joinPoint)) {
            return handleMonoReturn(joinPoint, operationLog, null, startTime, traceId, tenantIdAtEntry);
        }
        OperationLogEntity logEntity = buildLogEntity(joinPoint, operationLog, traceId);

        try {
            setRequestInfo(logEntity, joinPoint, operationLog);
        } catch (Exception e) {
            log.warn("初始化操作日志请求信息失败: {}", e.getClass().getSimpleName());
        }

        return handleSyncReturn(joinPoint, operationLog, logEntity, startTime, traceId, tenantIdAtEntry);
    }

    /**
     * 处理 {@link Mono} 返回值类型的目标方法
     *
     * <p>通过 {@code flatMap} 在 Mono 完成后记录日志，并通过 {@code onErrorResume}
     * 在出错时记录错误信息和用户信息。使用 {@code enrichUserInfo} 从
     * {@link ReactiveSecurityContextHolder} 中异步获取当前用户信息，
     * 避免阻塞事件循环线程。
     *
     * @param joinPoint  连接点
     * @param operationLog 操作日志注解
     * @param logEntity  日志实体（已包含请求信息）
     * @param startTime  开始时间
     * @param traceId    跟踪 ID
     * @param tenantIdAtEntry 切面入口捕获的租户 ID（异步落库时透传）
     * @return 包装了日志记录逻辑的 {@link Mono}
     * @throws Throwable 目标方法执行异常
     */
    @SuppressWarnings("unchecked")
    private Object handleMonoReturn(
            ProceedingJoinPoint joinPoint,
            OperationLog operationLog,
            OperationLogEntity logEntity,
            long startTime,
            String traceId,
            String tenantIdAtEntry)
            throws Throwable {
        Mono<Object> source = (Mono<Object>) joinPoint.proceed();
        return Mono.deferContextual(context -> {
            OperationLogEntity entry;
            try {
                entry = buildLogEntity(joinPoint, operationLog, traceId);
                setRequestInfo(entry, joinPoint, operationLog);
            } catch (RuntimeException ignored) {
                return source;
            }
            long subscriptionStart = System.currentTimeMillis();
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, tenantIdAtEntry);
            return ReactiveSecurityContextHolder.getContext()
                    .doOnNext(security -> {
                        Authentication authentication = security.getAuthentication();
                        if (authentication != null && authentication.isAuthenticated()) {
                            entry.setUserName(LogSafetyUtil.text(authentication.getName(), 128));
                            if (authentication.getPrincipal()
                                    instanceof com.apex.scaffold.interfaces.security.AuthenticatedUser user) {
                                entry.setUserId(user.userId());
                            }
                        }
                    })
                    .onErrorResume(error -> Mono.empty())
                    .then(source.doOnSuccess(result ->
                                    recordCompletion(entry, operationLog, result, null, subscriptionStart, tenant))
                            .doOnError(error ->
                                    recordCompletion(entry, operationLog, null, error, subscriptionStart, tenant)));
        });
    }

    private void recordCompletion(
            OperationLogEntity entry,
            OperationLog annotation,
            Object result,
            Throwable error,
            long start,
            String tenant) {
        try {
            entry.setStatus(error == null ? 0 : 1);
            if (error != null) {
                entry.setErrorMsg(error.getClass().getSimpleName());
            } else if (logResult && annotation.saveResponse() && result != null) {
                entry.setResponseResult(
                        LogSafetyUtil.json(result, maxResultLength, annotation.excludeResponseFields()));
            }
            completeLog(entry, start);
            enqueueLog(entry, tenant);
        } catch (RuntimeException ignored) {
            return;
        }
    }

    /**
     * 处理同步返回值类型的目标方法
     *
     * <p>使用 try-catch-finally 结构同步记录日志。
     *
     * @param joinPoint  连接点
     * @param operationLog 操作日志注解
     * @param logEntity  日志实体
     * @param startTime  开始时间
     * @param traceId    跟踪 ID
     * @param tenantIdAtEntry 切面入口捕获的租户 ID（异步落库时透传）
     * @return 目标方法执行结果
     * @throws Throwable 目标方法执行异常
     */
    private Object handleSyncReturn(
            ProceedingJoinPoint joinPoint,
            OperationLog operationLog,
            OperationLogEntity logEntity,
            long startTime,
            String traceId,
            String tenantIdAtEntry)
            throws Throwable {
        try {
            Object result = joinPoint.proceed();
            logEntity.setStatus(0);
            if (logResult && operationLog.saveResponse() && result != null) {
                try {
                    logEntity.setResponseResult(
                            LogSafetyUtil.json(result, maxResultLength, operationLog.excludeResponseFields()));
                } catch (RuntimeException ignored) {
                    logEntity.setResponseResult(null);
                }
            }
            return result;
        } catch (Exception e) {
            logEntity.setStatus(1);
            logEntity.setErrorMsg(e.getClass().getSimpleName());
            throw e;
        } finally {
            enrichUserInfoSync(logEntity);
            completeLog(logEntity, startTime);
            enqueueLog(logEntity, tenantIdAtEntry);
        }
    }

    /**
     * 从同步安全上下文中获取当前用户信息，补全日志实体的 userName。
     *
     * <p>该方法通过 {@link SecurityContextHolder} 获取当前认证信息，
     * 将认证主体的名称（openId）写入 {@code userName}。
     * 由于同步路径无法调用响应式 {@code userService.getByOpenId}，
     * {@code userId} 在此路径下无法解析，但至少 {@code userName} 不再为 NULL。
     *
     * @param logEntity 日志实体
     */
    private void enrichUserInfoSync(OperationLogEntity logEntity) {
        if (logEntity.getUserId() != null) {
            return;
        }
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                logEntity.setUserName(LogSafetyUtil.text(auth.getName(), 128));
                if (auth.getPrincipal() instanceof com.apex.scaffold.interfaces.security.AuthenticatedUser user) {
                    logEntity.setUserId(user.userId());
                }
            }
        } catch (Exception e) {
            log.debug("同步路径补充操作日志用户信息失败: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 判断目标方法返回类型是否为 {@link Mono}
     *
     * @param joinPoint 连接点
     * @return 若返回类型为 {@link Mono} 或其子类则返回 {@code true}
     */
    private boolean isMonoReturnType(ProceedingJoinPoint joinPoint) {
        if (joinPoint.getSignature() instanceof MethodSignature methodSignature) {
            return Mono.class.isAssignableFrom(methodSignature.getReturnType());
        }
        return false;
    }

    /**
     * 构建日志实体
     *
     * @param joinPoint    连接点
     * @param operationLog 操作日志注解
     * @param traceId      跟踪 ID
     * @return 操作日志实体
     */
    private OperationLogEntity buildLogEntity(
            ProceedingJoinPoint joinPoint, OperationLog operationLog, String traceId) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setModule(operationLog.module());
        entity.setOperationType(operationLog.type().name());
        entity.setDescription(operationLog.description());
        entity.setRequestMethod("INTERNAL");
        entity.setRequestUri(truncateString(joinPoint.getSignature().toShortString(), 500));
        entity.setTraceId(LogSafetyUtil.text(traceId, 64));
        return entity;
    }

    /**
     * 设置请求信息
     *
     * <p>从方法参数中提取 {@link ServerWebExchange}、{@link ServerHttpRequest} 或
     * {@link ServerRequest}，设置请求 URI、客户端 IP、请求方法、请求参数、
     * 操作地点和 User-Agent 解析结果。
     *
     * @param logEntity 日志实体
     * @param joinPoint 连接点
     * @param operationLog 操作日志注解
     */
    private void setRequestInfo(
            OperationLogEntity logEntity, ProceedingJoinPoint joinPoint, OperationLog operationLog) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return;
            }

            ServerHttpRequest request = AspectRequestUtil.resolveServerHttpRequest(args);
            if (request != null) {
                logEntity.setRequestUri(LogSafetyUtil.uri(String.valueOf(request.getURI()), 500));
                logEntity.setRequestMethod(truncateString(request.getMethod().name(), 10));
                logEntity.setIp(LogSafetyUtil.text(RequestUtil.getClientIp(request), 64));
                String userAgent = LogSafetyUtil.text(request.getHeaders().getFirst("User-Agent"), 512);
                // 基于 IP 设置操作地点
                if (logEntity.getIp() != null && !logEntity.getIp().isBlank()) {
                    logEntity.setLocation(resolveLocation(logEntity.getIp()));
                }
                // 解析 User-Agent 获取设备类型、浏览器和操作系统
                if (userAgent != null && !userAgent.isBlank()) {
                    parseUserAgent(logEntity, userAgent);
                }
            }

            if (logParams && operationLog.saveRequest() && args.length > 0) {
                logEntity.setRequestParams(LogSafetyUtil.json(
                        Arrays.stream(args)
                                .limit(32)
                                .filter(a -> !(a instanceof ServerWebExchange)
                                        && !(a instanceof ServerHttpRequest)
                                        && !(a instanceof ServerRequest))
                                .toList(),
                        maxParamLength,
                        operationLog.excludeRequestFields()));
            }
        } catch (Exception e) {
            log.warn("设置请求信息失败: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 根据 IP 地址解析操作地点。
     *
     * <p>当前实现仅区分内网 IP 和公网 IP：
     * <ul>
     *   <li>本地回环（127.x.x.x）→ "本地"</li>
     *   <li>内网地址（10.x / 172.16-31.x / 192.168.x）→ "内网"</li>
     *   <li>公网地址 → "未知"（需引入 geo-IP 库方可精确解析）</li>
     *   <li>unknown → null</li>
     * </ul>
     *
     * @param ip 客户端 IP 地址
     * @return 操作地点描述，无法识别时返回 "未知"
     */
    private String resolveLocation(String ip) {
        if (ip == null || ip.isBlank() || IP_UNKNOWN.equalsIgnoreCase(ip)) {
            return null;
        }
        // 本地回环
        if (ip.startsWith(IP_PREFIX_LOOPBACK) || IPV6_LOCALHOST_FULL.equals(ip) || IPV6_LOCALHOST_SHORT.equals(ip)) {
            return "本地";
        }
        // 内网地址
        if (ip.startsWith(IP_PREFIX_PRIVATE_10) || ip.startsWith(IP_PREFIX_PRIVATE_192)) {
            return "内网";
        }
        if (ip.startsWith(IP_PREFIX_PRIVATE_172)) {
            String[] parts = ip.split("\\.");
            if (parts.length >= IP_SPLIT_PARTS_THRESHOLD) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= IP_PRIVATE_172_RANGE_START && second <= IP_PRIVATE_172_RANGE_END) {
                        return "内网";
                    }
                } catch (NumberFormatException ignored) {
                    // 非标准 IPv4，忽略
                }
            }
        }
        return "未知";
    }

    /**
     * 解析 User-Agent 字符串，提取设备类型、浏览器和操作系统信息并设置到日志实体。
     *
     * <p>采用声明式规则表（{@link UaRule}）+ {@link Predicate} 按序匹配识别主流浏览器、
     * 操作系统与设备类型，无需引入第三方 UA 解析库。规则表在类加载时构建，
     * 每条规则表达「命中即返回」的匹配语义，消除手写 if-else 链的维护成本。</p>
     *
     * @param logEntity 日志实体
     * @param userAgent User-Agent 字符串
     */
    private void parseUserAgent(OperationLogEntity logEntity, String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return;
        }
        logEntity.setDeviceType(matchDeviceType(userAgent));
        logEntity.setBrowser(matchBrowser(userAgent));
        logEntity.setOs(matchOs(userAgent));
    }

    /**
     * UA 识别规则：由命中断言与目标取值构成的有序规则项。
     *
     * @param matcher 命中断言（接收 User-Agent 原文）
     * @param value   命中时输出的目标值
     */
    private record UaRule(Predicate<String> matcher, String value) {

        /** 判断给定 User-Agent 是否命中本规则。 */
        boolean matches(String userAgent) {
            return matcher.test(userAgent);
        }
    }

    /** 设备类型规则表：按序首匹配，Mobile 优先于 Tablet/Desktop。 */
    private static final List<UaRule> DEVICE_TYPE_RULES = List.of(
            new UaRule(ua -> ua.contains("Mobile") || ua.contains("Android") && !ua.contains("Tablet"), "Mobile"),
            new UaRule(ua -> ua.contains("iPad") || ua.contains("Tablet"), "Tablet"),
            new UaRule(ua -> true, "Desktop"));

    /** 浏览器规则表：按序首匹配，Edge 需在 Chrome 前（Edge UA 同时含 Chrome 标记）。 */
    private static final List<UaRule> BROWSER_RULES = List.of(
            new UaRule(ua -> ua.contains("Edg/"), "Edge"),
            new UaRule(ua -> ua.contains("Chrome/") && !ua.contains("Edg/"), "Chrome"),
            new UaRule(ua -> ua.contains("Safari/") && !ua.contains("Chrome/"), "Safari"),
            new UaRule(ua -> ua.contains("Firefox/"), "Firefox"),
            new UaRule(ua -> ua.contains("OPR/") || ua.contains("Opera/"), "Opera"),
            new UaRule(ua -> ua.contains("MicroMessenger/"), "WeChat"),
            new UaRule(ua -> ua.contains("QQ/"), "QQ"),
            new UaRule(ua -> true, "Unknown"));

    /** 操作系统规则表：按序首匹配，Android 需在 Linux 前（Android 基于 Linux）。 */
    private static final List<UaRule> OS_RULES = List.of(
            new UaRule(ua -> ua.contains("Windows NT"), "Windows"),
            new UaRule(ua -> ua.contains("Mac OS X") || ua.contains("macOS"), "macOS"),
            new UaRule(ua -> ua.contains("Android"), "Android"),
            new UaRule(ua -> ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS"), "iOS"),
            new UaRule(ua -> ua.contains("Linux") && !ua.contains("Android"), "Linux"),
            new UaRule(ua -> true, "Unknown"));

    /**
     * 按规则表首匹配输出设备类型。
     *
     * @param userAgent User-Agent 原文（已校验非空）
     * @return 设备类型，规则表兜底返回 Desktop
     */
    private static String matchDeviceType(String userAgent) {
        return DEVICE_TYPE_RULES.stream()
                .filter(rule -> rule.matches(userAgent))
                .findFirst()
                .map(UaRule::value)
                .orElse("Desktop");
    }

    /**
     * 按规则表首匹配输出浏览器名称。
     *
     * @param userAgent User-Agent 原文（已校验非空）
     * @return 浏览器名称，规则表兜底返回 Unknown
     */
    private static String matchBrowser(String userAgent) {
        return BROWSER_RULES.stream()
                .filter(rule -> rule.matches(userAgent))
                .findFirst()
                .map(UaRule::value)
                .orElse("Unknown");
    }

    /**
     * 按规则表首匹配输出操作系统名称。
     *
     * @param userAgent User-Agent 原文（已校验非空）
     * @return 操作系统名称，规则表兜底返回 Unknown
     */
    private static String matchOs(String userAgent) {
        return OS_RULES.stream()
                .filter(rule -> rule.matches(userAgent))
                .findFirst()
                .map(UaRule::value)
                .orElse("Unknown");
    }

    /**
     * 设置响应信息
     *
     * <p>将方法返回值序列化为 JSON 并存入日志实体。
     *
     * @param logEntity 日志实体
     * @param result    方法返回值
     */
    private void setResponseInfo(OperationLogEntity logEntity, Object result) {
        try {
            if (result != null) {
                String resultStr = LogSafetyUtil.json(result, maxResultLength);
                logEntity.setResponseResult(truncateString(resultStr, maxResultLength));
            }
        } catch (Exception e) {
            log.warn("设置响应信息失败: {}", e.getClass().getSimpleName(), e);
        }
    }

    /**
     * 补齐日志信息
     *
     * @param logEntity 日志实体
     * @param startTime 开始时间
     */
    private void completeLog(OperationLogEntity logEntity, long startTime) {
        logEntity.setExecutionTime(System.currentTimeMillis() - startTime);
        LocalDateTime now = LocalDateTime.now();
        logEntity.setCreateTime(now);
        logEntity.setOperationTime(now);
    }

    /**
     * 从响应式安全上下文中获取当前用户信息，补全日志实体的 userId 和 userName。
     *
     * <p>该方法通过 {@link ReactiveSecurityContextHolder} 获取当前认证信息，
     * 将 JWT 中的 openId（Principal Name）转换为用户实体，提取 userId 和昵称。
     * 仅在响应式上下文中可用。获取失败或未认证时静默跳过，不影响主流程。
     *
     * @param logEntity 日志实体
     */
    private Mono<Void> enrichUserInfo(OperationLogEntity logEntity) {
        if (logEntity.getUserId() != null) {
            return Mono.empty();
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(Objects::nonNull)
                .map(Authentication::getName)
                .flatMap(userService::getByOpenId)
                .doOnNext(user -> {
                    logEntity.setUserId(user.getId());
                    logEntity.setUserName(user.getNickName());
                })
                .then()
                .onErrorResume(e -> {
                    log.debug(
                            "Failed to enrich operation log user info: {}",
                            e.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    /**
     * 将日志实体放入队列
     *
     * @param logEntity      日志实体
     * @param tenantIdAtEntry 切面入口捕获的租户 ID（可能为 null）；在写入线程显式安装
     */
    private void enqueueLog(OperationLogEntity logEntity, String tenantIdAtEntry) {
        try {
            operationLogService.saveAsync(logEntity, tenantIdAtEntry);
        } catch (RuntimeException ignored) {
            return;
        }
    }

    /**
     * 截断字符串
     *
     * @param str    原始字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
    private String truncateString(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return LogSafetyUtil.text(str, maxLen);
    }
}
