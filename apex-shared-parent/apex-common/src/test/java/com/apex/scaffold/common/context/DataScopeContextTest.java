package com.apex.scaffold.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.enums.DataScopeType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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

            assertThat(DataScopeContext.currentDataScope()).hasValueSatisfying(info -> {
                assertThat(info.type()).isEqualTo(DataScopeType.DEPT);
                assertThat(info.deptId()).isEqualTo(100L);
            });
            assertThat(DataScopeContext.currentConfig()).hasValueSatisfying(config -> {
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
            assertThat(DataScopeContext.currentDataScope()).hasValueSatisfying(info -> {
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
    @DisplayName("作用域句柄")
    class ScopeHandleTest {

        @Test
        void shouldSnapshotScopeValues() {
            List<Long> deptIds = new ArrayList<>(List.of(100L));
            DataScopeContext.Scope scope = new DataScopeContext.Scope(
                    new DataScopeInfo(DataScopeType.CUSTOM, null, null, deptIds),
                    new DataScopeConfig("u", "u", "dept_id", "user_id"));
            deptIds.add(101L);

            assertThat(scope.info().deptIds()).containsExactly(100L);
            assertThat(scope.config().deptAlias()).isEqualTo("u");
        }

        @Test
        void shouldRestoreNestedScopeWhenHandlesClose() throws Exception {
            DataScopeContext.Scope outer =
                    new DataScopeContext.Scope(DataScopeInfo.ofDept(100L), DataScopeConfig.defaults());
            DataScopeContext.Scope inner =
                    new DataScopeContext.Scope(DataScopeInfo.ofSelf(200L), DataScopeConfig.defaults());

            try (AutoCloseable ignored = DataScopeContext.install(outer)) {
                assertThat(DataScopeContext.currentScope()).contains(outer);
                try (AutoCloseable nested = DataScopeContext.install(inner)) {
                    assertThat(DataScopeContext.currentScope()).contains(inner);
                }
                assertThat(DataScopeContext.currentScope()).contains(outer);
            }

            assertThat(DataScopeContext.currentScope()).isEmpty();
        }

        @Test
        void shouldRestoreScopeAfterException() {
            DataScopeContext.Scope outer =
                    new DataScopeContext.Scope(DataScopeInfo.ofDept(100L), DataScopeConfig.defaults());

            assertThatThrownBy(() -> {
                        try (AutoCloseable ignored = DataScopeContext.install(outer)) {
                            throw new IllegalStateException("boom");
                        }
                    })
                    .isInstanceOf(IllegalStateException.class);
            assertThat(DataScopeContext.currentScope()).isEmpty();
        }

        @Test
        void shouldKeepScopesThreadIsolated() throws Exception {
            DataScopeContext.Scope scope =
                    new DataScopeContext.Scope(DataScopeInfo.ofDept(100L), DataScopeConfig.defaults());
            AtomicReference<DataScopeContext.Scope> workerScope = new AtomicReference<>();

            try (AutoCloseable ignored = DataScopeContext.install(scope)) {
                Thread worker = new Thread(
                        () -> workerScope.set(DataScopeContext.currentScope().orElse(null)));
                worker.start();
                worker.join();
                assertThat(DataScopeContext.currentScope()).contains(scope);
            }

            assertThat(workerScope).hasValue(null);
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
