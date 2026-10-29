package com.apex.scaffold.officeservice.config;

import com.apex.scaffold.common.context.TenantContext;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对站内信主体与接收人表实施失败关闭的租户 SQL 约束。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class MessageTenantConfig {
    /**
     * 创建仅作用于站内信表的隔离拦截器。
     * @return SQL 拦截链
     */
    @Bean
    public MybatisPlusInterceptor messageTenantInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            /** @return 当前可信租户，缺失时拒绝数据库访问。 */
            @Override
            public Expression getTenantId() {
                return new StringValue(
                        TenantContext.currentTenantId().orElseThrow(() -> new IllegalStateException("站内信访问缺少可信租户")));
            }

            /**
             * 仅隔离本次修复涉及的消息表。
             * @param tableName 数据表
             * @return 是否跳过
             */
            @Override
            public boolean ignoreTable(String tableName) {
                return !"off_message".equalsIgnoreCase(tableName)
                        && !"off_message_recipient".equalsIgnoreCase(tableName);
            }
        }));
        interceptor.addInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor());
        return interceptor;
    }
}
