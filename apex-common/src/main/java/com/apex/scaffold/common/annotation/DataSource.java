package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据源动态切换注解（自定义数据源路由）。
 *
 * <p>标注在 Service 方法（或类）上，由 {@code DataSourceSwitchAspect} 切面在方法执行期间
 * 将数据源切换到指定名称（对应 dynamic-datasource 配置中 {@code spring.datasource.dynamic.datasource}
 * 下的键，如 {@code master} / {@code slave}），方法结束后自动切回；未指定时使用
 * {@code primary} 主数据源。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 读操作走从库
 * @DataSource("slave")
 * public Mono<UserDTO> getUserFromSlave(Long id) { ... }
 *
 * // 写操作显式走主库（与类级默认相反时）
 * @DataSource("master")
 * public Mono<Void> updateUser(Long id, ...) { ... }
 * }</pre>
 *
 * <p><b>执行语义</b>：与 {@code @DS}（Baomidou dynamic-datasource 内置注解）等价，
 * 但为本脚手架自研实现：支持响应式方法（{@code doFirst} 装配 / {@code doFinally} 释放）、
 * 方法级优先于类级、切换埋点（{@code apex.datasource.switch.count{target}}）与统一日志。
 * 切换引擎仍复用 Baomidou 的 {@code DynamicRoutingDataSource} 路由实现。</p>
 *
 * <p><b>响应式注意事项</b>：切换基于线程上下文（ThreadLocal），要求查询与装配同线程执行；
 * 经 {@code BlockingIoSchedulers.jdbc()} 切换线程的阻塞查询需要显式装配或线程上下文透明传播，
 * 详见 {@code docs/DATA_SOURCE_SWITCH_USAGE.md}。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataSource {

    /**
     * 数据源名称。
     *
     * <p>对应 dynamic-datasource 配置键；为空/空白时使用 {@code primary} 主数据源
     * （切面不执行切换）。默认 {@code master} 与项目主数据源命名一致。</p>
     *
     * @return 数据源名称，如 {@code master}、{@code slave}
     */
    String value() default "master";
}
