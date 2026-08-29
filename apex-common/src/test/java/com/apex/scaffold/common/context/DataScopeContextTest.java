package com.apex.scaffold.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.enums.DataScopeType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 数据权限上下文测试。
 *
 * <p>验证 {@link DataScopeContext} 的装配/读取/释放生命周期，以及旧版
 * {@code setDeptId}/{@code currentDeptId} API 的向后兼容性。
 *
 * @author Apex Team
 */
@DisplayName("DataScopeContext 测试")
class DataScopeContextTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Nested
    @DisplayName("装配与读取")
    class SetAndGetTest {

        @Test
        void shouldReturnEmptyWhenNotAssembled() {
            assertThat(DataScopeContext.currentDataScope()).isEmpty();
            assertThat(DataScopeContext.currentConfig()).isEmpty();
            assertThat(DataScopeContext.isActive()).isFalse();
        }

        @Test
        void shouldAssembleInfoAndDefaultConfig() {
            DataScopeContext.setDataScope(DataScopeInfo.ofDept(100L));

            assertThat(DataScopeContext.currentDataScope())
                    .hasValueSatisfying(info -> {
                        assertThat(info.type()).isEqualTo(DataScopeType.DEPT);
                        assertThat(info.deptId()).isEqualTo(100L);
                    });
            assertThat(DataScopeContext.currentConfig())
                    .hasValueSatisfying(config -> {
                        assertThat(config.deptField()).isEqualTo("dept_id");
                        assertThat(config.deptAlias()).isEmpty();
                    });
            assertThat(DataScopeContext.isActive()).isTrue();
        }

        @Test
        void shouldAssembleInfoWithCustomConfig() {
            DataScopeContext.setDataScope(
                    DataScopeInfo.ofDeptAndChild(100L, List.of(100L, 101L)),
                    new DataScopeConfig("u", "u", "dept_id", "user_id"));

            assertThat(DataScopeContext.currentConfig())
                    .hasValueSatisfying(config -> assertThat(config.deptAlias()).isEqualTo("u"));
        }

        @Test
        void shouldConsiderAllScopeInactive() {
            DataScopeContext.setDataScope(DataScopeInfo.all());
            assertThat(DataScopeContext.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("旧版部门 API 兼容")
    class LegacyApiTest {

        @Test
        void shouldKeepSetAndGetDeptIdWorking() {
            DataScopeContext.setDeptId(100L);

            assertThat(DataScopeContext.currentDeptId()).hasValue(100L);
            assertThat(DataScopeContext.currentDataScope())
                    .hasValueSatisfying(info -> {
                        assertThat(info.type()).isEqualTo(DataScopeType.DEPT);
                        assertThat(info.deptId()).isEqualTo(100L);
                    });
        }

        @Test
        void shouldReturnEmptyDeptIdWhenNotSet() {
            assertThat(DataScopeContext.currentDeptId()).isEmpty();
        }
    }

    @Nested
    @DisplayName("释放")
    class ClearTest {

        @Test
        void shouldClearContext() {
            DataScopeContext.setDataScope(DataScopeInfo.ofDept(100L));
            DataScopeContext.clear();

            assertThat(DataScopeContext.currentDataScope()).isEmpty();
            assertThat(DataScopeContext.currentDeptId()).isEmpty();
            assertThat(DataScopeContext.isActive()).isFalse();
        }
    }
}
