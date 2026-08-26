package com.apex.scaffold.infrastructure.mq.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link MqConsumerService.ProcessingResult} 的单元测试类
 *
 * <p>测试消息处理结果的四种工厂方法及状态判断。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("MqConsumerService.ProcessingResult 单元测试")
class MqConsumerServiceTest {

    @Nested
    @DisplayName("ProcessingResult.success 方法")
    class SuccessTests {

        /**
         * 执行 {@code shouldCreateSuccessResult} 操作。
         */
        @Test
        @DisplayName("应创建包含正确 messageId 和 payload 的成功结果")
        void shouldCreateSuccessResult() {
            MqConsumerService.ProcessingResult<String> result =
                    MqConsumerService.ProcessingResult.success("msg-1", "payload-data");

            assertThat(result.getMessageId()).isEqualTo("msg-1");
            assertThat(result.getPayload()).isEqualTo("payload-data");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isDuplicate()).isFalse();
            assertThat(result.getErrorMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("ProcessingResult.failed 方法")
    class FailedTests {

        /**
         * 执行 {@code shouldCreateFailedResult} 操作。
         */
        @Test
        @DisplayName("应创建包含失败原因的失败结果")
        void shouldCreateFailedResult() {
            MqConsumerService.ProcessingResult<Void> result =
                    MqConsumerService.ProcessingResult.failed("msg-1", "业务处理返回false");

            assertThat(result.getMessageId()).isEqualTo("msg-1");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isDuplicate()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("业务处理返回false");
        }
    }

    @Nested
    @DisplayName("ProcessingResult.error 方法")
    class ErrorTests {

        /**
         * 执行 {@code shouldCreateErrorResult} 操作。
         */
        @Test
        @DisplayName("应创建包含异常信息的错误结果")
        void shouldCreateErrorResult() {
            MqConsumerService.ProcessingResult<Void> result =
                    MqConsumerService.ProcessingResult.error("msg-1", "NullPointerException");

            assertThat(result.getMessageId()).isEqualTo("msg-1");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isDuplicate()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("NullPointerException");
        }
    }

    @Nested
    @DisplayName("ProcessingResult.duplicate 方法")
    class DuplicateTests {

        /**
         * 执行 {@code shouldCreateDuplicateResult} 操作。
         */
        @Test
        @DisplayName("应创建标记为重复消息的结果")
        void shouldCreateDuplicateResult() {
            MqConsumerService.ProcessingResult<Void> result = MqConsumerService.ProcessingResult.duplicate("msg-1");

            assertThat(result.getMessageId()).isEqualTo("msg-1");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.getErrorMessage()).isNull();
            assertThat(result.getPayload()).isNull();
        }
    }
}
