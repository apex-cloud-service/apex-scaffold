package com.apex.scaffold.infrastructure.persistence.datapermission;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.DataScopeContext;
import com.apex.scaffold.common.context.DataScopeInfo;
import com.apex.scaffold.common.enums.DataScopeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * 默认数据权限装配器（MDC）测试。
 *
 * <p>验证 {@link MdcDataScopeProvider} 的解析顺序（ThreadLocal 优先、MDC 兜底）、
 * MDC 键的解析与格式非法时的降级。
 *
 * @author Apex Team
 */
@DisplayName("MdcDataScopeProvider 测试")
class MdcDataScopeProviderTest {

    private final MdcDataScopeProvider provider = new MdcDataScopeProvider();

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
        MDC.remove(MdcDataScopeProvider.MDC_USER_ID);
        MDC.remove(MdcDataScopeProvider.MDC_DEPT_ID);
        MDC.remove(MdcDataScopeProvider.MDC_DATA_SCOPE);
        MDC.remove(MdcDataScopeProvider.MDC_DATA_SCOPE_DEPT_IDS);
    }

    @Nested
    @DisplayName("ThreadLocal 优先")
    class ThreadLocalPrecedenceTest {

        @Test
        void shouldPreferExplicitThreadLocalOverMdc() {
            DataScopeContext.setDataScope(DataScopeInfo.ofDept(100L));
            MDC.put(MdcDataScopeProvider.MDC_DEPT_ID, "999");

            assertThat(provider.resolve())
                    .hasValueSatisfying(info -> {
                        assertThat(info.type()).isEqualTo(DataScopeType.DEPT);
                        assertThat(info.deptId()).isEqualTo(100L);
                    });
        }
    }

    @Nested
    @DisplayName("MDC 解析")
    class MdcResolutionTest {

        @Test
        void shouldResolveDeptScopeFromMdc() {
            MDC.put(MdcDataScopeProvider.MDC_USER_ID, "42");
            MDC.put(MdcDataScopeProvider.MDC_DEPT_ID, "100");

            assertThat(provider.resolve())
                    .hasValueSatisfying(info -> {
                        // 未显式声明类型 → 回退为"本部门数据权限"（DEPT 范围不使用 userId）
                        assertThat(info.type()).isEqualTo(DataScopeType.DEPT);
                        assertThat(info.deptId()).isEqualTo(100L);
                    });
        }

        @Test
        void shouldPreserveUserIdWhenExplicitTypeGiven() {
            MDC.put(MdcDataScopeProvider.MDC_USER_ID, "42");
            MDC.put(MdcDataScopeProvider.MDC_DEPT_ID, "100");
            MDC.put(MdcDataScopeProvider.MDC_DATA_SCOPE, "2");

            assertThat(provider.resolve())
                    .hasValueSatisfying(info -> {
                        assertThat(info.type()).isEqualTo(DataScopeType.DEPT);
                        assertThat(info.userId()).isEqualTo(42L);
                        assertThat(info.deptId()).isEqualTo(100L);
                    });
        }

        @Test
        void shouldResolveExplicitTypeAndDeptIdsFromMdc() {
            MDC.put(MdcDataScopeProvider.MDC_DATA_SCOPE, "3");
            MDC.put(MdcDataScopeProvider.MDC_DEPT_ID, "100");
            MDC.put(MdcDataScopeProvider.MDC_DATA_SCOPE_DEPT_IDS, "100, 101, 102");

            assertThat(provider.resolve())
                    .hasValueSatisfying(info -> {
                        assertThat(info.type()).isEqualTo(DataScopeType.DEPT_AND_CHILD);
                        assertThat(info.deptIds()).containsExactly(100L, 101L, 102L);
                    });
        }

        @Test
        void shouldResolveCustomScopeFromMdc() {
            MDC.put(MdcDataScopeProvider.MDC_DATA_SCOPE, "5");
            MDC.put(MdcDataScopeProvider.MDC_DATA_SCOPE_DEPT_IDS, "1,2,3");

            assertThat(provider.resolve())
                    .hasValueSatisfying(info -> {
                        assertThat(info.type()).isEqualTo(DataScopeType.CUSTOM);
                        assertThat(info.deptIds()).containsExactly(1L, 2L, 3L);
                    });
        }

        @Test
        void shouldResolveAllScopeFromMdc() {
            MDC.put(MdcDataScopeProvider.MDC_DATA_SCOPE, "1");

            assertThat(provider.resolve())
                    .hasValueSatisfying(info -> assertThat(info.type()).isEqualTo(DataScopeType.ALL));
        }
    }

    @Nested
    @DisplayName("空值与非法格式")
    class EmptyAndInvalidTest {

        @Test
        void shouldReturnEmptyWhenNothingAvailable() {
            assertThat(provider.resolve()).isEmpty();
        }

        @Test
        void shouldIgnoreInvalidNumbersGracefully() {
            MDC.put(MdcDataScopeProvider.MDC_DATA_SCOPE, "99");
            MDC.put(MdcDataScopeProvider.MDC_DEPT_ID, "not-a-number");
            MDC.put(MdcDataScopeProvider.MDC_DATA_SCOPE_DEPT_IDS, "1,abc,3,");

            assertThat(provider.resolve()).isEmpty();
        }

        @Test
        void shouldSkipInvalidIdsInDeptIdList() {
            MDC.put(MdcDataScopeProvider.MDC_DATA_SCOPE, "3");
            MDC.put(MdcDataScopeProvider.MDC_DATA_SCOPE_DEPT_IDS, "100, ,abc,102");

            assertThat(provider.resolve())
                    .hasValueSatisfying(info -> assertThat(info.deptIds()).containsExactly(100L, 102L));
        }
    }
}
