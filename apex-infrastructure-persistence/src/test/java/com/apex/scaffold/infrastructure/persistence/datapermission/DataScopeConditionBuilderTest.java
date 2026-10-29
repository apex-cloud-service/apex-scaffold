package com.apex.scaffold.infrastructure.persistence.datapermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeInfo;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 数据权限过滤条件构建器测试。
 *
 * <p>验证 {@link DataScopeConditionBuilder} 对五种数据权限类型生成的条件片段、
 * 别名/字段配置与缺失关键值时的降级行为。
 *
 * @author Apex Team
 */
@DisplayName("DataScopeConditionBuilder 测试")
class DataScopeConditionBuilderTest {

    private static final DataScopeConfig DEFAULT_CONFIG = DataScopeConfig.defaults();

    @Nested
    @DisplayName("本部门数据权限")
    class DeptScopeTest {

        @Test
        void shouldBuildDeptEqualsCondition() {
            assertThat(DataScopeConditionBuilder.build(DEFAULT_CONFIG, DataScopeInfo.ofDept(100L)))
                    .isEqualTo("dept_id = 100");
        }

        @Test
        void shouldPrefixAliasWhenConfigured() {
            DataScopeConfig config = new DataScopeConfig("u", "u", "dept_id", "user_id");
            assertThat(DataScopeConditionBuilder.build(config, DataScopeInfo.ofDept(100L)))
                    .isEqualTo("u.dept_id = 100");
        }

        @Test
        void shouldUseCustomDeptField() {
            DataScopeConfig config = new DataScopeConfig("", "", "org_id", "user_id");
            assertThat(DataScopeConditionBuilder.build(config, DataScopeInfo.ofDept(100L)))
                    .isEqualTo("org_id = 100");
        }

        @Test
        void shouldRejectWhenDeptIdMissing() {
            DataScopeInfo scope =
                    new DataScopeInfo(com.apex.scaffold.common.enums.DataScopeType.DEPT, null, null, List.of());

            assertThatThrownBy(() -> DataScopeConditionBuilder.build(DEFAULT_CONFIG, scope))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DEPT")
                    .hasMessageContaining("deptId");
        }
    }

    @Nested
    @DisplayName("本部门及以下数据权限")
    class DeptAndChildScopeTest {

        @Test
        void shouldBuildInClauseWithDeptIds() {
            DataScopeInfo scope = DataScopeInfo.ofDeptAndChild(100L, List.of(100L, 101L, 102L));
            assertThat(DataScopeConditionBuilder.build(DEFAULT_CONFIG, scope)).isEqualTo("dept_id IN (100, 101, 102)");
        }

        @Test
        void shouldRejectWhenDeptIdsAreEmpty() {
            DataScopeInfo scope = DataScopeInfo.ofDeptAndChild(100L, List.of());
            assertThatThrownBy(() -> DataScopeConditionBuilder.build(DEFAULT_CONFIG, scope))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DEPT_AND_CHILD")
                    .hasMessageContaining("deptIds");
        }

        @Test
        void shouldRejectWhenNoDeptInfo() {
            DataScopeInfo scope = DataScopeInfo.ofDeptAndChild(null, List.of());
            assertThatThrownBy(() -> DataScopeConditionBuilder.build(DEFAULT_CONFIG, scope))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DEPT_AND_CHILD")
                    .hasMessageContaining("deptIds");
        }
    }

    @Nested
    @DisplayName("仅本人数据权限")
    class SelfScopeTest {

        @Test
        void shouldBuildSelfEqualsCondition() {
            assertThat(DataScopeConditionBuilder.build(DEFAULT_CONFIG, DataScopeInfo.ofSelf(42L)))
                    .isEqualTo("user_id = 42");
        }

        @Test
        void shouldUseUserAlias() {
            DataScopeConfig config = new DataScopeConfig("", "u", "dept_id", "user_id");
            assertThat(DataScopeConditionBuilder.build(config, DataScopeInfo.ofSelf(42L)))
                    .isEqualTo("u.user_id = 42");
        }

        @Test
        void shouldRejectWhenUserIdMissing() {
            DataScopeInfo scope =
                    new DataScopeInfo(com.apex.scaffold.common.enums.DataScopeType.SELF, null, null, List.of());
            assertThatThrownBy(() -> DataScopeConditionBuilder.build(DEFAULT_CONFIG, scope))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SELF")
                    .hasMessageContaining("userId");
        }
    }

    @Nested
    @DisplayName("自定义数据权限")
    class CustomScopeTest {

        @Test
        void shouldBuildInClauseWithCustomDeptIds() {
            DataScopeInfo scope = DataScopeInfo.ofCustom(List.of(1L, 2L, 3L));
            assertThat(DataScopeConditionBuilder.build(DEFAULT_CONFIG, scope)).isEqualTo("dept_id IN (1, 2, 3)");
        }

        @Test
        void shouldRejectWhenCustomDeptIdsAreEmpty() {
            assertThatThrownBy(() -> DataScopeConditionBuilder.build(DEFAULT_CONFIG, DataScopeInfo.ofCustom(List.of())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CUSTOM")
                    .hasMessageContaining("deptIds");
        }
    }

    @Nested
    @DisplayName("全部数据权限与空输入")
    class AllAndNullTest {

        @Test
        void shouldReturnNullForAllScope() {
            assertThat(DataScopeConditionBuilder.build(DEFAULT_CONFIG, DataScopeInfo.all()))
                    .isNull();
        }

        @Test
        void shouldReturnNullForNullScopeOrConfig() {
            assertThat(DataScopeConditionBuilder.build(null, DataScopeInfo.ofDept(100L)))
                    .isNull();
            assertThat(DataScopeConditionBuilder.build(DEFAULT_CONFIG, null)).isNull();
        }
    }
}
