package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.application.service.OperationLogService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.common.util.RequestUtil;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
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
 * <h2>核心功能</h2>
 * <ul>
 *   <li>自动记录用户操作的模块、类型、描述、参数 IP 等信息</li>
 *   <li>对返回类型为 {@link Mono} 的方法，使用响应式的 {@code doOnSuccess} / {@code doOnError}
 *       在异步操作完成后记录日志，而非同步阻塞等待</li>
 *   <li>异步批量写入数据库，通过阻塞队列实现生产-消费模式</li>
 *   <li>支持日志批量合并，减少数据库写入次数</li>
 * </ul>
 *
 * <h2>设计模式</h2>
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

    /** Jackson ObjectMapper */
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
     * @param joinPoint    连接点
     * @param operationLog 操作日志注解
     * @return 方法执行结果
     * @throws Throwable 执行异常
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        if (!operationLogEnabled) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        String traceId = TraceIdUtil.getTraceId();

        OperationLogEntity logEntity = buildLogEntity(joinPoint, operationLog, traceId);

        try {
            setRequestInfo(logEntity, joinPoint);
        } catch (Exception e) {
            log.warn("初始化操作日志请求信息失败: {}", e.getMessage());
        }

        // 判断返回类型是否为 Mono，采用不同的处理策略
        boolean isMonoReturn = isMonoReturnType(joinPoint);

        if (isMonoReturn) {
            return handleMonoReturn(joinPoint, logEntity, startTime, traceId);
        } else {
            return handleSyncReturn(joinPoint, logEntity, startTime, traceId);
        }
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
     * @param logEntity  日志实体（已包含请求信息）
     * @param startTime  开始时间
     * @param traceId    跟踪 ID
     * @return 包装了日志记录逻辑的 {@link Mono}
     * @throws Throwable 目标方法执行异常
     */
    @SuppressWarnings("unchecked")
    private Object handleMonoReturn(
            ProceedingJoinPoint joinPoint, OperationLogEntity logEntity, long startTime, String traceId)
            throws Throwable {
        return ((Mono<Object>) joinPoint.proceed())
                .flatMap(result -> {
                    logEntity.setStatus(0);
                    if (logResult && result != null) {
                        setResponseInfo(logEntity, result);
                    }
                    completeLog(logEntity, startTime);
                    return enrichUserInfo(logEntity)
                            .doFinally(s -> enqueueLog(logEntity))
                            .thenReturn(result);
                })
                .onErrorResume(error -> {
                    logEntity.setStatus(1);
                    logEntity.setErrorMsg(truncateString(error.getMessage(), 2000));
                    completeLog(logEntity, startTime);
                    return enrichUserInfo(logEntity)
                            .doFinally(s -> enqueueLog(logEntity))
                            .then(Mono.error(error));
                });
    }

    /**
     * 处理同步返回值类型的目标方法
     *
     * <p>使用 try-catch-finally 结构同步记录日志。
     *
     * @param joinPoint  连接点
     * @param logEntity  日志实体
     * @param startTime  开始时间
     * @param traceId    跟踪 ID
     * @return 目标方法执行结果
     * @throws Throwable 目标方法执行异常
     */
    private Object handleSyncReturn(
            ProceedingJoinPoint joinPoint, OperationLogEntity logEntity, long startTime, String traceId)
            throws Throwable {
        try {
            Object result = joinPoint.proceed();
            logEntity.setStatus(0);
            if (logResult && result != null) {
                setResponseInfo(logEntity, result);
            }
            return result;
        } catch (Exception e) {
            logEntity.setStatus(1);
            logEntity.setErrorMsg(truncateString(e.getMessage(), 2000));
            throw e;
        } finally {
            enrichUserInfoSync(logEntity);
            completeLog(logEntity, startTime);
            enqueueLog(logEntity);
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
                logEntity.setUserName(auth.getName());
            }
        } catch (Exception e) {
            log.debug("同步路径补充操作日志用户信息失败: {}", e.getMessage());
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
        entity.setTraceId(traceId);
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
     */
    private void setRequestInfo(OperationLogEntity logEntity, ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return;
            }

            String userAgent = null;
            for (Object arg : args) {
                if (arg instanceof ServerWebExchange exchange) {
                    ServerHttpRequest request = exchange.getRequest();
                    logEntity.setRequestUri(truncateString(String.valueOf(request.getURI()), 500));
                    logEntity.setRequestMethod(
                            truncateString(request.getMethod().name(), 10));
                    logEntity.setIp(RequestUtil.getClientIp(request));
                    userAgent = request.getHeaders().getFirst("User-Agent");
                    break;
                } else if (arg instanceof ServerHttpRequest request) {
                    logEntity.setRequestUri(truncateString(String.valueOf(request.getURI()), 500));
                    logEntity.setRequestMethod(
                            truncateString(request.getMethod().name(), 10));
                    logEntity.setIp(RequestUtil.getClientIp(request));
                    userAgent = request.getHeaders().getFirst("User-Agent");
                    break;
                } else if (arg instanceof ServerRequest serverRequest) {
                    ServerWebExchange exchange = serverRequest.exchange();
                    ServerHttpRequest request = exchange.getRequest();
                    logEntity.setRequestUri(truncateString(String.valueOf(request.getURI()), 500));
                    logEntity.setRequestMethod(
                            truncateString(request.getMethod().name(), 10));
                    logEntity.setIp(RequestUtil.getClientIp(request));
                    userAgent = request.getHeaders().getFirst("User-Agent");
                    break;
                }
            }

            // 基于 IP 设置操作地点
            if (logEntity.getIp() != null && !logEntity.getIp().isBlank()) {
                logEntity.setLocation(resolveLocation(logEntity.getIp()));
            }

            // 解析 User-Agent 获取设备类型、浏览器和操作系统
            if (userAgent != null && !userAgent.isBlank()) {
                parseUserAgent(logEntity, userAgent);
            }

            if (logParams && args.length > 0) {
                String params = truncateString(
                        JsonUtil.toJson(Arrays.stream(args)
                                .filter(a -> !(a instanceof ServerWebExchange)
                                        && !(a instanceof ServerHttpRequest)
                                        && !(a instanceof ServerRequest))
                                .toList()),
                        maxParamLength);
                logEntity.setRequestParams(params);
            }
        } catch (Exception e) {
            log.warn("设置请求信息失败: {}", e.getMessage());
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
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return null;
        }
        // 本地回环
        if (ip.startsWith("127.") || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "本地";
        }
        // 内网地址
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return "内网";
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
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
     * <p>采用简单规则匹配方式识别主流浏览器和操作系统，无需引入第三方 UA 解析库。
     *
     * @param logEntity 日志实体
     * @param userAgent User-Agent 字符串
     */
    private void parseUserAgent(OperationLogEntity logEntity, String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return;
        }

        // 识别设备类型
        if (userAgent.contains("Mobile") || userAgent.contains("Android") && !userAgent.contains("Tablet")) {
            logEntity.setDeviceType("Mobile");
        } else if (userAgent.contains("iPad") || userAgent.contains("Tablet")) {
            logEntity.setDeviceType("Tablet");
        } else {
            logEntity.setDeviceType("Desktop");
        }

        // 识别浏览器
        if (userAgent.contains("Edg/")) {
            logEntity.setBrowser("Edge");
        } else if (userAgent.contains("Chrome/") && !userAgent.contains("Edg/")) {
            logEntity.setBrowser("Chrome");
        } else if (userAgent.contains("Safari/") && !userAgent.contains("Chrome/")) {
            logEntity.setBrowser("Safari");
        } else if (userAgent.contains("Firefox/")) {
            logEntity.setBrowser("Firefox");
        } else if (userAgent.contains("OPR/") || userAgent.contains("Opera/")) {
            logEntity.setBrowser("Opera");
        } else if (userAgent.contains("MicroMessenger/")) {
            logEntity.setBrowser("WeChat");
        } else if (userAgent.contains("QQ/")) {
            logEntity.setBrowser("QQ");
        } else {
            logEntity.setBrowser("Unknown");
        }

        // 识别操作系统
        if (userAgent.contains("Windows NT")) {
            logEntity.setOs("Windows");
        } else if (userAgent.contains("Mac OS X") || userAgent.contains("macOS")) {
            logEntity.setOs("macOS");
        } else if (userAgent.contains("Android")) {
            logEntity.setOs("Android");
        } else if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iOS")) {
            logEntity.setOs("iOS");
        } else if (userAgent.contains("Linux") && !userAgent.contains("Android")) {
            logEntity.setOs("Linux");
        } else {
            logEntity.setOs("Unknown");
        }
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
                String resultStr = JsonUtil.toJson(result);
                logEntity.setResponseResult(truncateString(resultStr, maxResultLength));
            }
        } catch (Exception e) {
            log.warn("设置响应信息失败: {}", e.getMessage(), e);
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
     * @return 完成时发出空信号的 Mono
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
                    log.debug("Failed to enrich operation log user info: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 将日志实体放入队列
     *
     * @param logEntity 日志实体
     */
    private void enqueueLog(OperationLogEntity logEntity) {
        operationLogService.saveAsync(logEntity);
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
        if (str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen);
    }
}
