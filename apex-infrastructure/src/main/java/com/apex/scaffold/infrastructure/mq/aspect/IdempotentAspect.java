package com.apex.scaffold.infrastructure.mq.aspect;

import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.infrastructure.mq.annotation.Idempotent;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 分布式幂等性切面
 *
 * <p>基于 Redis（Redisson）实现消息消费的幂等性控制，防止 RocketMQ 消息重复消费。
 * 相比本地内存方案，Redis 分布式存储确保多实例部署下幂等记录共享一致。
 *
 * <h2>工作原理</h2>
 * <ol>
 *   <li>拦截标注 {@link Idempotent} 注解的方法</li>
 *   <li>根据注解中的 key 表达式或方法参数生成全局唯一的幂等键</li>
 *   <li>通过 Redisson 的 {@code setIfAbsent} 原子操作尝试标记该键为"已处理"</li>
 *   <li>如果键已存在（表示消息已被处理），则直接返回 {@code null} 跳过执行</li>
 *   <li>如果键不存在，则正常执行业务逻辑，Redis 键会在过期时间后自动清理</li>
 * </ol>
 *
 * <h2>键命名规范</h2>
 * <pre>
 *   idempotent:mq:{业务键}
 * </pre>
 *
 * <h2>幂等键生成策略</h2>
 * <ul>
 *   <li>如果注解中指定了 key 且匹配参数名，使用该参数值作为幂等键</li>
 *   <li>如果未指定 key，使用"方法签名+参数哈希"作为默认幂等键</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see Idempotent
 * @see RedissonClient
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnBean(RocketMQTemplate.class)
public class IdempotentAspect {

    /** Redis 幂等键前缀 */
    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:mq:";

    /** Redisson 客户端，提供分布式 Redis 存储能力 */
    private final RedissonClient redissonClient;

    /**
     * 构造幂等性切面
     *
     * @param redissonClient Redisson 客户端
     */
    public IdempotentAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 幂等性环绕通知
     *
     * <p>拦截所有标注 {@link Idempotent} 注解的方法，执行分布式幂等性检查。
     * 如果消息已被处理过，直接返回 {@code null} 跳过消费逻辑；
     * 如果处理过程中抛出异常，清除 Redis 中的幂等标记以允许重试。
     *
     * @param joinPoint 连接点，提供目标方法的执行上下文
     * @return 方法执行结果，重复消息返回 {@code null}
     * @throws Throwable 目标方法执行过程中的异常
     */
    @Around("@annotation(com.apex.scaffold.infrastructure.mq.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        String key = parseKey(idempotent.key(), joinPoint);
        if (key == null || key.isEmpty()) {
            log.warn("幂等键解析失败，跳过幂等性检查");
            return joinPoint.proceed();
        }

        String idempotentKey = IDEMPOTENT_KEY_PREFIX + key;
        long expireTime = idempotent.expireTime();

        RBucket<String> bucket = redissonClient.getBucket(idempotentKey, StringCodec.INSTANCE);
        boolean acquired = bucket.setIfAbsent("processed", Duration.ofSeconds(expireTime));

        if (!acquired) {
            log.warn("消息已处理，跳过重复处理，key: {}", key);
            return null;
        }

        try {
            Object result = joinPoint.proceed();
            log.info("消息处理成功并标记为已处理，key: {}", key);
            return result;
        } catch (Exception e) {
            log.error("消息处理异常，key: {}", key, e);
            bucket.delete();
            throw e;
        }
    }

    /**
     * 解析幂等键
     *
     * <p>根据注解配置的 key 表达式，从方法参数中提取幂等键。
     * 如果 key 表达式为空，则使用方法签名和完整参数序列化字符串的组合键。
     * 使用完整 JSON 序列化字符串代替 {@code hashCode()} 以避免哈希冲突。
     *
     * @param keyExpression 注解中配置的 key 表达式
     * @param joinPoint     连接点
     * @return 解析后的幂等键字符串，解析失败返回 {@code null}
     */
    private String parseKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        if (keyExpression == null || keyExpression.isEmpty()) {
            StringBuilder defaultKey = new StringBuilder();
            defaultKey.append(joinPoint.getSignature().toShortString());

            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                defaultKey.append("_").append(JsonUtil.toJson(args));
            }
            return defaultKey.toString();
        }

        try {
            Object[] args = joinPoint.getArgs();
            String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();

            if (args != null && paramNames != null) {
                return IntStream.range(0, args.length)
                        .filter(i -> paramNames[i].equals(keyExpression))
                        .mapToObj(i -> args[i] != null ? args[i].toString() : null)
                        .findFirst()
                        .orElse(null);
            }
            return keyExpression;
        } catch (Exception e) {
            log.error("解析幂等键表达式失败：{}", keyExpression, e);
            return null;
        }
    }
}
