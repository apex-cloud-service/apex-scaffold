package com.apex.scaffold.infrastructure.persistence.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 *
 * <p>配置 MyBatis-Plus 扩展插件，包括：
 * <ul>
 *   <li><strong>乐观锁插件</strong>：{@link OptimisticLockerInnerInterceptor}，
 *       自动为标注了 {@code @Version} 注解的字段生成乐观锁 SQL</li>
 * </ul>
 *
 * <p>分页插件由 MyBatis-Plus Starter 自动配置，无需手动添加。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusConfig {
    private static final Logger log = LoggerFactory.getLogger(MybatisPlusConfig.class);

    /** 构造 MyBatis-Plus 配置类 */
    public MybatisPlusConfig() {}

    /**
     * 注册 MyBatis-Plus 拦截器链（含乐观锁插件）
     *
     * <p>在 MyBatis-Plus 3.5.x 中，所有内部拦截器需通过 {@link MybatisPlusInterceptor}
     * 统一注册。乐观锁插件自动为 {@code UPDATE} 语句追加 {@code WHERE version = oldVersion}
     * 条件，更新时同时执行 {@code SET version = version + 1}。
     * MyBatis-Plus 原生只在冲突时返回 0 行，因此另由
     * {@link com.apex.scaffold.infrastructure.persistence.aspect.OptimisticLockUpdateAspect} 转换为
     * {@link org.springframework.dao.OptimisticLockingFailureException}。
     *
     * <p>配合 {@link com.apex.scaffold.common.annotation.OptimisticLockRetry} 注解使用，
     * 可自动重试解决乐观锁冲突。
     *
     * @return MyBatis-Plus 拦截器链实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        log.info("MyBatis-Plus 乐观锁插件已注册");
        return interceptor;
    }
}
