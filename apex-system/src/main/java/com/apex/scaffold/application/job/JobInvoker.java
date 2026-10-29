package com.apex.scaffold.application.job;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.JobEntity;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 定时任务调用目标解析与执行器。
 *
 * <p>负责将任务的 {@code invokeTarget} 描述解析为 Spring Bean 方法并反射调用。
 * 支持两种格式：</p>
 * <ul>
 *   <li>{@code beanName.methodName} — 无参方法</li>
 *   <li>{@code beanName.methodName("str", 123, true, 1.5)} — 带字符串/整数/布尔/小数参数</li>
 * </ul>
 *
 * <p>方法返回类型为 {@link reactor.core.publisher.Mono} 或
 * {@link reactor.core.publisher.Flux} 时自动订阅并阻塞等待完成，
 * 保证任务执行日志能记录真实耗时；其余类型忽略返回值。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobInvoker {

    private static final String ARG_SEPARATOR = " ";
    private static final String ARG_DELIMITER = ",";
    private static final String BOOLEAN_TRUE = "true";
    private static final String BOOLEAN_FALSE = "false";
    /** 参数列表左括号，用于定位调用目标中的参数起点。 */
    private static final String OPEN_PAREN = "(";
    /** 参数列表右括号，用于判定表达式是否闭合。 */
    private static final String CLOSE_PAREN = ")";

    private static final String INTEGER_PATTERN = "-?\\d+";
    private static final String DECIMAL_PATTERN = "-?\\d+\\.\\d+";

    /**
     * Spring 应用上下文，用于按 Bean 名称查找任务目标。
     */
    private final ApplicationContext applicationContext;

    /**
     * 执行任务调用目标。
     *
     * <p>解析失败或调用异常时抛出 {@link IllegalStateException}，
     * 由调度器捕获并记录失败日志。</p>
     *
     * @param job 任务实体，{@code invokeTarget} 非空
     * @throws IllegalStateException 当目标解析失败、Bean 不存在或方法调用异常时抛出
     */
    public void invoke(JobEntity job) {
        if (job == null || !StringUtils.hasText(job.getInvokeTarget())) {
            throw new IllegalStateException("任务调用目标不能为空");
        }
        if (!StringUtils.hasText(job.getTenantId())) {
            throw new IllegalStateException("租户任务缺少租户ID");
        }
        String previousTenant = TenantContext.currentTenantId().orElse(null);
        TenantContext.setTenantId(job.getTenantId());
        try {
            Invocation invocation = parse(job.getInvokeTarget());
            Object bean = resolveBean(invocation.beanName());
            Object result = invokeMethod(bean, invocation);
            awaitIfPublisher(result, job.getTenantId());
        } finally {
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenant);
            }
        }
    }

    /**
     * 保存前验证任务方法白名单，不执行业务方法。
     *
     * @param target 调用目标字符串
     * @throws IllegalStateException 目标不存在、参数不匹配或方法未声明白名单
     */
    public void validateTarget(String target) {
        if (!StringUtils.hasText(target)) {
            throw new IllegalStateException("任务调用目标不能为空");
        }
        Invocation invocation = parse(target);
        Object bean = resolveBean(invocation.beanName());
        requireManagedMethod(bean, invocation);
    }

    /**
     * 解析受控任务表达式，并拒绝不完整的参数括号。
     *
     * @param target 调用目标
     * @return 解析后的调用描述
     */
    private Invocation parse(String target) {
        String expression = target.trim();
        if (expression.contains(OPEN_PAREN) && !expression.endsWith(CLOSE_PAREN)) {
            throw new IllegalStateException("任务参数括号未闭合");
        }
        int parenIndex = expression.indexOf(OPEN_PAREN);
        String callablePart = parenIndex >= 0 ? expression.substring(0, parenIndex) : expression;
        String argsPart = parenIndex >= 0
                ? expression.substring(parenIndex + 1, expression.length() - 1).trim()
                : "";

        int dotIndex = callablePart.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == callablePart.length() - 1) {
            throw new IllegalStateException("调用目标格式非法，应为 beanName.methodName: " + target);
        }
        String beanName = callablePart.substring(0, dotIndex).trim();
        String methodName = callablePart.substring(dotIndex + 1).trim();
        List<Object> args = parseArgs(argsPart);
        return new Invocation(beanName, methodName, args);
    }

    /**
     * 解析方法参数列表。
     *
     * <p>支持字符串（单双引号包裹）、整数、小数、布尔值；逗号分隔。</p>
     *
     * @param argsText 参数文本
     * @return 参数列表
     */
    private List<Object> parseArgs(String argsText) {
        List<Object> args = new ArrayList<>(16);
        if (argsText.isEmpty()) {
            return args;
        }
        int index = 0;
        while (index < argsText.length()) {
            char c = argsText.charAt(index);
            if (c == '\'' || c == '"') {
                int end = argsText.indexOf(c, index + 1);
                if (end < 0) {
                    throw new IllegalStateException("字符串参数缺少闭合引号: " + argsText);
                }
                args.add(argsText.substring(index + 1, end));
                index = end + 1;
            } else {
                int comma = argsText.indexOf(',', index);
                String token = (comma < 0 ? argsText.substring(index) : argsText.substring(index, comma)).trim();
                if (!token.isEmpty()) {
                    args.add(coerce(token));
                }
                index = comma < 0 ? argsText.length() : comma + 1;
            }
            while (index < argsText.length()
                    && (argsText.charAt(index) == ARG_SEPARATOR.charAt(0)
                            || argsText.charAt(index) == ARG_DELIMITER.charAt(0))) {
                index++;
            }
        }
        return args;
    }

    /**
     * 将文本参数推断为具体类型。
     *
     * @param token 参数文本
     * @return 推断后的对象（整数/小数/布尔/字符串）
     */
    private Object coerce(String token) {
        if (BOOLEAN_TRUE.equalsIgnoreCase(token) || BOOLEAN_FALSE.equalsIgnoreCase(token)) {
            return Boolean.parseBoolean(token);
        }
        if (token.matches(INTEGER_PATTERN)) {
            try {
                return Long.parseLong(token);
            } catch (NumberFormatException ignored) {
                return Integer.parseInt(token);
            }
        }
        if (token.matches(DECIMAL_PATTERN)) {
            return Double.parseDouble(token);
        }
        return token;
    }

    /**
     * 按名称解析 Spring Bean。
     *
     * @param beanName Bean 名称
     * @return Bean 实例
     * @throws IllegalStateException 当 Bean 不存在时抛出
     */
    private Object resolveBean(String beanName) {
        if (!applicationContext.containsBean(beanName)) {
            throw new IllegalStateException("Spring Bean 不存在: " + beanName);
        }
        return applicationContext.getBean(beanName);
    }

    /**
     * 反射调用目标方法。
     *
     * <p>优先按"方法名 + 参数类型"精确匹配，找不到时退化为按方法名
     * 匹配首个可接受参数个数的方法。</p>
     *
     * @param bean Bean 实例
     * @param invocation 调用描述
     * @return 方法返回值，可能为 {@code null}
     * @throws IllegalStateException 当方法不存在或调用失败时抛出
     */
    private Object invokeMethod(Object bean, Invocation invocation) {
        Method method = requireManagedMethod(bean, invocation);
        try {
            method.setAccessible(true);
            return method.invoke(bean, invocation.args().toArray());
        } catch (Exception e) {
            throw new IllegalStateException("调用任务方法失败: " + invocation.beanName() + "." + invocation.methodName(), e);
        }
    }

    /**
     * 解析并检查白名单，再选择代理上可调用的方法。
     *
     * @param bean Bean 实例
     * @param invocation 调用描述
     * @return 匹配的方法
     * @throws IllegalStateException 当找不到匹配方法时抛出
     */
    private Method requireManagedMethod(Object bean, Invocation invocation) {
        Method method = findMethod(org.springframework.aop.support.AopUtils.getTargetClass(bean), invocation);
        if (!method.isAnnotationPresent(ManagedJob.class)) {
            throw new IllegalStateException("任务方法未授权");
        }
        return org.springframework.aop.support.AopUtils.selectInvocableMethod(method, bean.getClass());
    }

    /**
     * 根据名称和参数类型解析唯一可调用方法。
     *
     * @param beanClass 业务 Bean 类型
     * @param invocation 调用描述
     * @return 匹配方法
     */
    private Method findMethod(Class<?> beanClass, Invocation invocation) {
        List<Object> args = invocation.args();
        Class<?>[] paramTypes = args.stream().map(Object::getClass).toArray(Class[]::new);

        try {
            return beanClass.getMethod(invocation.methodName(), paramTypes);
        } catch (NoSuchMethodException e) {
            for (Method candidate : beanClass.getMethods()) {
                if (candidate.getName().equals(invocation.methodName())
                        && candidate.getParameterCount() == args.size()
                        && java.util.stream.IntStream.range(0, args.size())
                                .allMatch(index -> org.springframework.util.ClassUtils.isAssignableValue(
                                        candidate.getParameterTypes()[index], args.get(index)))) {
                    return candidate;
                }
            }
            throw new IllegalStateException(
                    "任务方法不存在: " + invocation.beanName() + "." + invocation.methodName() + "(" + args.size() + "参数)");
        }
    }

    /**
     * 方法返回响应式类型时订阅并阻塞等待完成。
     *
     * @param result 方法返回值
     * @param tenantId 租户 ID，响应式链路等待期间透传租户上下文
     */
    private void awaitIfPublisher(Object result, String tenantId) {
        if (result instanceof org.reactivestreams.Publisher<?> publisher) {
            TenantContext.withTenant(reactor.core.publisher.Flux.from(publisher)
                            .then()
                            .contextWrite(TenantContext.withTenant(tenantId)))
                    .block(java.time.Duration.ofMinutes(5));
        }
    }

    /**
     * 调用描述记录。
     *
     * @param beanName Bean 名称
     * @param methodName 方法名称
     * @param args 方法参数
     */
    private record Invocation(String beanName, String methodName, List<Object> args) {}
}
