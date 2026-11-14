package com.apex.scaffold.common.autoconfigure;

import com.apex.scaffold.common.audit.ReactiveOperationLogAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 装配限定服务包的操作审计切面，避免依赖 common 组件扫描。
 *
 * <p>切面不匹配 system、office、workflow，保留这些服务的既有审计消费者。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@AutoConfiguration(after = AopAutoConfiguration.class)
public class OperationAuditAutoConfiguration {
    /**
     * 创建白名单审计消费者，不引入业务服务依赖或认证要求。
     *
     * @return 响应式操作日志切面
     */
    @Bean
    @ConditionalOnMissingBean
    public ReactiveOperationLogAspect reactiveOperationLogAspect() {
        return new ReactiveOperationLogAspect();
    }
}
