package com.apex.scaffold.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.enums.DataScopeType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 数据权限信息值对象测试。
 *
 * <p>验证 {@link DataScopeInfo} 的静态工厂、{@code deptIds} 规范化、
 * {@code effectiveDeptIds()} 回退逻辑与不可变性。
 *
 * @author Apex Team
 */
@DisplayName("DataScopeInfo 测试")
class DataScopeInfoTest {

    @Nested
    @DisplayName("静态工厂")
    class FactoryTest {

        @Test
        void shouldBuildAllScope() {
            DataScopeInfo info = DataScopeInfo.all();
            assertThat(info.type()).isEqualTo(DataScopeType.ALL);
            assertThat(info.isAll()).isTrue();
            assertThat(info.deptIds()).isEmpty();
        }

        @Test
        void shouldBuildDeptScope() {
            DataScopeInfo info = DataScopeInfo.ofDept(100L);
            assertThat(info.type()).isEqualTo(DataScopeType.DEPT);
            assertThat(info.deptId()).isEqualTo(100L);
            assertThat(info.isAll()).isFalse();
        }

        @Test
        void shouldBuildDeptAndChildScope() {
            DataScopeInfo info = DataScopeInfo.ofDeptAndChild(100L, List.of(100L, 101L, 102L));
            assertThat(info.type()).isEqualTo(DataScopeType.DEPT_AND_CHILD);
            assertThat(info.deptId()).isEqualTo(100L);
            assertThat(info.deptIds()).containsExactly(100L, 101L, 102L);
        }

        @Test
        void shouldBuildSelfScope() {
            DataScopeInfo info = DataScopeInfo.ofSelf(42L);
            assertThat(info.type()).isEqualTo(DataScopeType.SELF);
            assertThat(info.userId()).isEqualTo(42L);
        }

        @Test
        void shouldBuildCustomScope() {
            DataScopeInfo info = DataScopeInfo.ofCustom(List.of(1L, 2L, 3L));
            assertThat(info.type()).isEqualTo(DataScopeType.CUSTOM);
            assertThat(info.deptIds()).containsExactly(1L, 2L, 3L);
        }
    }

    @Nested
    @DisplayName("deptIds 规范化与不可变性")
    class NormalizationTest {

        @Test
        void shouldRejectNullType() {
            assertThatThrownBy(() -> new DataScopeInfo(null, 1L, 2L, List.of(3L)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldNormalizeNullDeptIdsToEmpty() {
            DataScopeInfo info = new DataScopeInfo(DataScopeType.CUSTOM, null, null, null);
            assertThat(info.deptIds()).isEmpty();
        }

        @Test
        void shouldReturnImmutableSnapshotOfDeptIds() {
            List<Long> mutable = new java.util.ArrayList<>(List.of(1L, 2L));
            DataScopeInfo info = new DataScopeInfo(DataScopeType.CUSTOM, null, null, mutable);
            mutable.add(3L);
            assertThat(info.deptIds()).containsExactly(1L, 2L);
            assertThatThrownBy(() -> info.deptIds().add(9L))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("effectiveDeptIds() 回退逻辑")
    class EffectiveDeptIdsTest {

        @Test
        void shouldReturnDeptIdsWhenPresent() {
            DataScopeInfo info = DataScopeInfo.ofDeptAndChild(100L, List.of(100L, 101L));
            assertThat(info.effectiveDeptIds()).containsExactly(100L, 101L);
        }

        @Test
        void shouldFallbackToDeptIdWhenDeptIdsEmpty() {
            DataScopeInfo info = DataScopeInfo.ofDept(100L);
            assertThat(info.effectiveDeptIds()).containsExactly(100L);
        }

        @Test
        void shouldReturnEmptyWhenNoDeptInfo() {
            DataScopeInfo info = DataScopeInfo.ofSelf(42L);
            assertThat(info.effectiveDeptIds()).isEmpty();
        }
    }
}
