package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * TraceIdUtil 单元测试
 *
 * <p>测试追踪ID的生成、设置、获取和清除功能，验证MDC操作的正确性。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("TraceIdUtil 单元测试")
class TraceIdUtilTest {

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    /**
     * 执行 {@code tearDown} 操作。
     */
    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("generateTraceId 生成追踪ID测试")
    class GenerateTraceIdTests {

        /**
         * 执行 {@code shouldGenerateTraceIdWithoutHyphens} 操作。
         */
        @Test
        @DisplayName("generateTraceId应生成不含横线的32位十六进制字符串")
        void shouldGenerateTraceIdWithoutHyphens() {
            String traceId = TraceIdUtil.generateTraceId();

            assertThat(traceId).isNotNull();
            assertThat(traceId).isNotEmpty();
            assertThat(traceId).hasSize(32);
            assertThat(traceId).doesNotContain("-");
            assertThat(traceId).matches("^[0-9a-f]{32}$");
        }

        /**
         * 执行 {@code shouldGenerateDifferentTraceIds} 操作。
         */
        @Test
        @DisplayName("两次调用generateTraceId应生成不同的ID")
        void shouldGenerateDifferentTraceIds() {
            String traceId1 = TraceIdUtil.generateTraceId();
            String traceId2 = TraceIdUtil.generateTraceId();

            assertThat(traceId1).isNotEqualTo(traceId2);
        }
    }

    @Nested
    @DisplayName("setTraceId / getTraceId 设置和获取追踪ID测试")
    class SetAndGetTraceIdTests {

        /**
         * 执行 {@code shouldReturnSetTraceId} 操作。
         */
        @Test
        @DisplayName("setTraceId设置后getTraceId应返回相同值")
        void shouldReturnSetTraceId() {
            String expected = "test-trace-id-001";

            TraceIdUtil.setTraceId(expected);
            String actual = TraceIdUtil.getTraceId();

            assertThat(actual).isEqualTo(expected);
        }

        /**
         * 执行 {@code shouldAutoGenerateTraceIdWhenNotSet} 操作。
         */
        @Test
        @DisplayName("getTraceId在未设置时应自动生成新ID")
        void shouldAutoGenerateTraceIdWhenNotSet() {
            String traceId = TraceIdUtil.getTraceId();

            assertThat(traceId).isNotNull();
            assertThat(traceId).isNotEmpty();
            assertThat(traceId).hasSize(32);
            assertThat(traceId).doesNotContain("-");
        }

        /**
         * 执行 {@code shouldReturnSameTraceIdOnMultipleCalls} 操作。
         */
        @Test
        @DisplayName("getTraceId连续调用应返回同一ID")
        void shouldReturnSameTraceIdOnMultipleCalls() {
            String traceId1 = TraceIdUtil.getTraceId();
            String traceId2 = TraceIdUtil.getTraceId();

            assertThat(traceId1).isEqualTo(traceId2);
        }

        /**
         * 执行 {@code shouldOverwriteTraceId} 操作。
         */
        @Test
        @DisplayName("setTraceId覆盖后getTraceId应返回最新值")
        void shouldOverwriteTraceId() {
            TraceIdUtil.setTraceId("first-id");
            TraceIdUtil.setTraceId("second-id");

            String actual = TraceIdUtil.getTraceId();

            assertThat(actual).isEqualTo("second-id");
        }
    }

    @Nested
    @DisplayName("clear 清除追踪ID测试")
    class ClearTraceIdTests {

        /**
         * 执行 {@code shouldGenerateNewTraceIdAfterClear} 操作。
         */
        @Test
        @DisplayName("clear后getTraceId应自动生成新ID")
        void shouldGenerateNewTraceIdAfterClear() {
            TraceIdUtil.setTraceId("existing-id");
            TraceIdUtil.clear();

            String traceId = TraceIdUtil.getTraceId();

            assertThat(traceId).isNotNull();
            assertThat(traceId).isNotEmpty();
            assertThat(traceId).hasSize(32);
            assertThat(traceId).isNotEqualTo("existing-id");
        }

        /**
         * 执行 {@code shouldNotThrowExceptionWhenClearingUnsetKey} 操作。
         */
        @Test
        @DisplayName("clear未设置的MDC不应抛异常")
        void shouldNotThrowExceptionWhenClearingUnsetKey() {
            assertThatCode(TraceIdUtil::clear).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("init 初始化追踪ID测试")
    class InitTraceIdTests {

        /**
         * 执行 {@code shouldInitTraceIdWhenNotSet} 操作。
         */
        @Test
        @DisplayName("init在未设置时应自动生成追踪ID")
        void shouldInitTraceIdWhenNotSet() {
            TraceIdUtil.init();

            String traceId = MDC.get(TraceIdUtil.TRACE_ID_KEY);

            assertThat(traceId).isNotNull();
            assertThat(traceId).isNotEmpty();
            assertThat(traceId).hasSize(32);
            assertThat(traceId).doesNotContain("-");
        }

        /**
         * 执行 {@code shouldNotOverwriteExistingTraceIdOnInit} 操作。
         */
        @Test
        @DisplayName("init在已设置时应保留已有ID不覆盖")
        void shouldNotOverwriteExistingTraceIdOnInit() {
            String existingId = "existing-trace-id";
            MDC.put(TraceIdUtil.TRACE_ID_KEY, existingId);

            TraceIdUtil.init();

            String traceId = MDC.get(TraceIdUtil.TRACE_ID_KEY);
            assertThat(traceId).isEqualTo(existingId);
        }
    }

    @Nested
    @DisplayName("TRACE_ID_KEY 常量测试")
    class ConstantTests {

        /**
         * 执行 {@code shouldHaveCorrectTraceIdKeyConstant} 操作。
         */
        @Test
        @DisplayName("TRACE_ID_KEY应为\"traceId\"")
        void shouldHaveCorrectTraceIdKeyConstant() {
            assertThat(TraceIdUtil.TRACE_ID_KEY).isEqualTo("traceId");
        }
    }
}
