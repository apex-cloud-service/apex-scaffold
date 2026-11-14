package com.apex.scaffold.infrastructure.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.infrastructure.persistence.datapermission.MdcDataScopeProvider;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class MybatisPlusConfigTest {

    @Test
    void registersTenantInterceptorWhenTenantIsolationIsEnabled() throws Exception {
        MybatisPlusConfig config = new MybatisPlusConfig(new DatabaseProperties());
        setTenantEnabled(config, true);

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(new MdcDataScopeProvider());

        assertThat(interceptor.getInterceptors()).anyMatch(TenantLineInnerInterceptor.class::isInstance);
    }

    @Test
    void doesNotRegisterTenantInterceptorWhenTenantIsolationIsDisabled() throws Exception {
        MybatisPlusConfig config = new MybatisPlusConfig(new DatabaseProperties());
        setTenantEnabled(config, false);

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(new MdcDataScopeProvider());

        assertThat(interceptor.getInterceptors()).noneMatch(TenantLineInnerInterceptor.class::isInstance);
    }

    @Test
    void registersMysqlPaginationInterceptorLast() {
        MybatisPlusConfig config = new MybatisPlusConfig(new DatabaseProperties());

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(new MdcDataScopeProvider());

        assertThat(interceptor.getInterceptors())
                .isNotEmpty()
                .last()
                .isInstanceOfSatisfying(
                        PaginationInnerInterceptor.class,
                        pagination -> assertThat(pagination.getDbType()).isEqualTo(DbType.MYSQL));
    }

    private void setTenantEnabled(MybatisPlusConfig config, boolean enabled) throws Exception {
        Field field = MybatisPlusConfig.class.getDeclaredField("tenantEnabled");
        field.setAccessible(true);
        field.setBoolean(config, enabled);
    }
}
