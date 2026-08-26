package com.apex.scaffold.infrastructure.persistence.aspect;

import com.apex.scaffold.common.annotation.AuditLog;
import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.infrastructure.persistence.po.AuditLogPO;
import com.apex.scaffold.infrastructure.persistence.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * 审计日志AOP切面
 *
 * <p>拦截标注了@AuditLog注解的方法,自动记录操作信息到数据库。
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 构建审计日志对象
        AuditLogPO logPO = buildAuditLog(joinPoint, auditLog);

        Object result = null;
        try {
            // 执行目标方法
            result = joinPoint.proceed();

            // 记录成功状态
            logPO.setStatus("SUCCESS");
            if (auditLog.recordResult() && result != null) {
                logPO.setResponseResult(objectMapper.writeValueAsString(result));
            }
        } catch (Exception e) {
            // 记录失败状态
            logPO.setStatus("FAIL");
            logPO.setErrorMsg(e.getMessage());
            throw e;
        } finally {
            // 计算执行时长
            long duration = System.currentTimeMillis() - startTime;
            logPO.setDuration(duration);

            // 异步保存审计日志(避免影响主业务流程)
            saveAuditLogAsync(logPO);
        }

        return result;
    }

    /**
     * 构建审计日志对象
     */
    private AuditLogPO buildAuditLog(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        AuditLogPO logPO = new AuditLogPO();

        // 基本信息
        logPO.setModule(auditLog.module());
        logPO.setOperationType(auditLog.type().name());
        logPO.setDescription(resolveDescription(auditLog.description(), joinPoint));

        // 方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        logPO.setMethod(method.getDeclaringClass().getName() + "." + method.getName());

        // 请求信息(WebFlux：从方法参数中提取 ServerWebExchange)
        ServerWebExchange exchange = findExchange(joinPoint);
        if (exchange != null) {
            ServerHttpRequest request = exchange.getRequest();
            logPO.setRequestUrl(request.getPath().value());
            logPO.setOperatorIp(getIpAddress(request));
        }

        // 操作人信息：从 JwtAuthenticationFilter 写入的 exchange attribute 获取当前用户
        String userId = exchange != null ? (String) exchange.getAttributes().get(SystemConstants.USER_ID_ATTR) : null;
        if (userId != null && !userId.isBlank()) {
            logPO.setOperatorId(userId);
            logPO.setOperatorName("用户:" + userId);
        } else {
            logPO.setOperatorId("anonymous");
            logPO.setOperatorName("匿名用户");
        }

        // 请求参数
        if (auditLog.recordParams()) {
            Map<String, Object> params = extractParams(joinPoint, auditLog.ignoreParams());
            try {
                logPO.setRequestParams(objectMapper.writeValueAsString(params));
            } catch (Exception e) {
                log.warn("序列化请求参数失败: {}", e.getMessage());
            }
        }

        // 其他字段
        logPO.setCreateTime(LocalDateTime.now());
        logPO.setDeleted(0);

        return logPO;
    }

    /**
     * 解析描述中的SpEL表达式
     */
    private String resolveDescription(String description, ProceedingJoinPoint joinPoint) {
        if (description == null || description.isEmpty()) {
            return "";
        }

        // 简单实现: 替换 #{#param} 格式的占位符
        // 完整实现需要使用Spring的SpEL表达式解析器
        String resolved = description;
        Object[] args = joinPoint.getArgs();
        String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                String placeholder = "#{" + paramNames[i] + "}";
                if (resolved.contains(placeholder)) {
                    try {
                        String value = objectMapper.writeValueAsString(args[i]);
                        resolved = resolved.replace(placeholder, value);
                    } catch (Exception e) {
                        resolved = resolved.replace(placeholder, args[i].toString());
                    }
                }
            }
        }

        return resolved;
    }

    /**
     * 提取请求参数
     */
    private Map<String, Object> extractParams(ProceedingJoinPoint joinPoint, String[] ignoreParams) {
        Map<String, Object> params = new HashMap<>();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                // 忽略指定的参数
                if (Arrays.asList(ignoreParams).contains(paramNames[i])) {
                    continue;
                }
                params.put(paramNames[i], args[i]);
            }
        }

        return params;
    }

    /**
     * 从方法参数中查找 WebFlux 的 {@link ServerWebExchange}。
     *
     * <p>WebFlux 无 Servlet 容器与 {@code RequestContextHolder}，请求上下文需通过
     * Controller 方法签名注入的 {@code ServerWebExchange} 参数获取。</p>
     *
     * @param joinPoint AOP 连接点
     * @return 方法参数中的 {@link ServerWebExchange}，未注入时返回 {@code null}
     */
    private ServerWebExchange findExchange(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof ServerWebExchange) {
                return (ServerWebExchange) arg;
            }
        }
        return null;
    }

    /**
     * 获取客户端IP地址
     */
    private String getIpAddress(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String ip = headers.getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = headers.getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = headers.getFirst("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = headers.getFirst("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            InetSocketAddress remoteAddress = request.getRemoteAddress();
            if (remoteAddress != null) {
                ip = remoteAddress.getAddress().getHostAddress();
            }
        }

        // 多个代理时,第一个IP为真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 异步保存审计日志
     */
    private void saveAuditLogAsync(AuditLogPO logPO) {
        // 简单实现: 直接同步保存
        // 生产环境建议使用@Async或消息队列异步处理
        auditLogService.saveAuditLog(logPO);
    }
}
