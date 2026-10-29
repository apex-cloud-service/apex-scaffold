package com.apex.scaffold.infrastructure.persistence.datapermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.DataScopeContext;
import com.apex.scaffold.common.context.DataScopeInfo;
import java.util.ArrayList;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 数据权限拦截器测试。
 *
 * <p>使用真实 {@link BoundSql} 验证 {@link DataScopeInnerInterceptor#beforeQuery}
 * 的完整链路：装配上下文 → 解析数据权限 → 改写 SQL → 反射回写 {@code BoundSql.sql}。
 *
 * @author Apex Team
 */
@DisplayName("DataScopeInnerInterceptor 测试")
class DataScopeInnerInterceptorTest {

    private final DataScopeInnerInterceptor interceptor = new DataScopeInnerInterceptor(new MdcDataScopeProvider());

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    /**
     * 构造真实 BoundSql 与 Mock MappedStatement。
     */
    private BoundSql boundSql(String sql) {
        return new BoundSql(new Configuration(), sql, new ArrayList<>(), new Object());
    }

    private MappedStatement mappedStatement() {
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getId()).thenReturn("com.apex.scaffold.infrastructure.persistence.mapper.UserMapper.selectPage");
        return ms;
    }

    @Nested
    @DisplayName("SQL 注入")
    class InjectionTest {

        @Test
        void shouldInjectDeptConditionWhenContextAssembled() {
            DataScopeContext.setDataScope(DataScopeInfo.ofDept(100L));
            BoundSql boundSql = boundSql("SELECT id, username, dept_id FROM sys_user WHERE deleted = 0");

            interceptor.beforeQuery(null, mappedStatement(), null, null, null, boundSql);

            assertThat(boundSql.getSql())
                    .isNotEqualTo("SELECT id, username, dept_id FROM sys_user WHERE deleted = 0")
                    .contains("dept_id = 100");
        }

        @Test
        void shouldInjectIntoQueryWithoutWhere() {
            DataScopeContext.setDataScope(DataScopeInfo.ofDeptAndChild(100L, java.util.List.of(100L, 101L)));
            BoundSql boundSql = boundSql("SELECT * FROM sys_user");

            interceptor.beforeQuery(null, mappedStatement(), null, null, null, boundSql);

            assertThat(boundSql.getSql()).contains("dept_id IN (100, 101)");
        }
    }

    @Nested
    @DisplayName("放行场景")
    class BypassTest {

        @Test
        void shouldNotTouchSqlWhenNoContext() {
            BoundSql boundSql = boundSql("SELECT * FROM sys_user WHERE deleted = 0");

            interceptor.beforeQuery(null, mappedStatement(), null, null, null, boundSql);

            assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM sys_user WHERE deleted = 0");
        }

        @Test
        void shouldNotTouchSqlWhenAllScope() {
            DataScopeContext.setDataScope(DataScopeInfo.all());
            BoundSql boundSql = boundSql("SELECT * FROM sys_user");

            interceptor.beforeQuery(null, mappedStatement(), null, null, null, boundSql);

            assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM sys_user");
        }

        @Test
        void shouldNotTouchSqlWhenProviderResolvesNothing() {
            // 未装配 ThreadLocal、未写 MDC → provider 返回空 → 放行
            BoundSql boundSql = boundSql("SELECT * FROM sys_user");

            interceptor.beforeQuery(null, mappedStatement(), null, null, null, boundSql);

            assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM sys_user");
        }
    }
}
