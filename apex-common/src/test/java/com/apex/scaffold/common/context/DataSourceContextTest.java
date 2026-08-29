package com.apex.scaffold.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 数据源切换上下文测试。
 *
 * <p>验证 {@link DataSourceContext} 的切换/回退/嵌套/释放生命周期，
 * 以及空白数据源名的忽略行为。
 *
 * @author Apex Team
 */
@DisplayName("DataSourceContext 测试")
class DataSourceContextTest {

    @AfterEach
    void tearDown() {
        DataSourceContext.clear();
    }

    @Nested
    @DisplayName("切换与读取")
    class SwitchAndReadTest {

        @Test
        void shouldReturnEmptyWhenNotSwitched() {
            assertThat(DataSourceContext.current()).isEmpty();
            assertThat(DataSourceContext.isSwitched()).isFalse();
        }

        @Test
        void shouldPushAndReadCurrent() {
            DataSourceContext.push("slave");

            assertThat(DataSourceContext.current()).hasValue("slave");
            assertThat(DataSourceContext.isSwitched()).isTrue();
        }

        @Test
        void shouldPopBackToPrevious() {
            DataSourceContext.push("slave");
            DataSourceContext.pop();

            assertThat(DataSourceContext.current()).isEmpty();
            assertThat(DataSourceContext.isSwitched()).isFalse();
        }

        @Test
        void shouldIgnoreBlankName() {
            DataSourceContext.push("  ");
            assertThat(DataSourceContext.current()).isEmpty();

            DataSourceContext.push(null);
            assertThat(DataSourceContext.current()).isEmpty();
        }
    }

    @Nested
    @DisplayName("嵌套切换")
    class NestedSwitchTest {

        @Test
        void shouldRestoreOuterAfterInnerPop() {
            DataSourceContext.push("slave");
            DataSourceContext.push("report");

            assertThat(DataSourceContext.current()).hasValue("report");

            DataSourceContext.pop();
            assertThat(DataSourceContext.current()).hasValue("slave");

            DataSourceContext.pop();
            assertThat(DataSourceContext.current()).isEmpty();
        }
    }

    @Nested
    @DisplayName("释放")
    class ClearTest {

        @Test
        void shouldClearAll() {
            DataSourceContext.push("slave");
            DataSourceContext.push("report");

            DataSourceContext.clear();

            assertThat(DataSourceContext.current()).isEmpty();
            assertThat(DataSourceContext.isSwitched()).isFalse();
        }

        @Test
        void shouldBeIdempotentOnEmptyPop() {
            DataSourceContext.pop();
            DataSourceContext.pop();
            assertThat(DataSourceContext.current()).isEmpty();
        }
    }
}
