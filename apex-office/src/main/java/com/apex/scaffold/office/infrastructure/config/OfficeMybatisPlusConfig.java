package com.apex.scaffold.office.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 办公库 MyBatis-Plus 插件配置。
 *
 * <p>办公库不引入 {@code apex-infrastructure-persistence} 的共享 {@code MybatisPlusConfig}
 * （该配置含租户行级插件）：office 沿用显式租户条件隔离口径，仓储查询条件自带
 * {@code tenant_id}，故不注册 {@code TenantLineInnerInterceptor}。本配置只注册两个
 * 结构性必需插件：
 *
 * <ul>
 *   <li><strong>乐观锁插件</strong>：带 {@code @Version} 的实体（审批流程、撤销编排、
 *       通知模板、会议室、日历、文档等 PO）执行 {@code updateById} 时，SQL 模板依赖
 *       {@code OptimisticLockerInnerInterceptor} 绑定版本参数；缺插件时更新直接抛
 *       {@code BindingException: MP_OPTLOCK_VERSION_ORIGINAL not found}；</li>
 *   <li><strong>分页插件</strong>：通知模板/投递记录等仓储使用 {@code selectPage}，
 *       缺插件时不生成 count 查询，分页结果丢失总数。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration(proxyBeanMethods = false)
public class OfficeMybatisPlusConfig {

    /**
     * 注册办公库必需的 MyBatis-Plus 内部插件链（乐观锁 + 分页）。
     *
     * <p>分页拦截器不显式指定 DbType：由 MyBatis-Plus 按数据源连接自动识别方言，
     * 与 office 的 MySQL/PostgreSQL 双库部署口径一致。
     *
     * @return 插件链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
