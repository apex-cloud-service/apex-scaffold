package com.apex.scaffold.infrastructure.mq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

/**
 * {@link MqProducerService} 的单元测试类
 *
 * <p>测试RocketMQ生产者服务：同步、异步、单向、延迟和带 Header 五种发送模式。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("MqProducerService 单元测试")
@ExtendWith(MockitoExtension.class)
class MqProducerServiceTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private SendResult sendResult;

    private MqProducerService service;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        service = new MqProducerService(rocketMQTemplate);
    }

    @Nested
    @DisplayName("sendSync 同步发送")
    class SendSyncTests {

        /**
         * 执行 {@code shouldSyncSendWithDestination} 操作。
         */
        @Test
        @DisplayName("应使用 topic:tag 作为destination同步发送")
        void shouldSyncSendWithDestination() {
            when(rocketMQTemplate.syncSend(anyString(), any(Message.class))).thenReturn(sendResult);

            SendResult result = service.sendSync("test-topic", "test-tag", "hello");

            assertThat(result).isSameAs(sendResult);
            verify(rocketMQTemplate).syncSend(eq("test-topic:test-tag"), any(Message.class));
        }

        /**
         * 执行 {@code shouldThrowOnFailure} 操作。
         */
        @Test
        @DisplayName("发送失败时应抛出异常")
        void shouldThrowOnFailure() {
            when(rocketMQTemplate.syncSend(anyString(), any(Message.class)))
                    .thenThrow(new RuntimeException("RocketMQ error"));

            assertThatThrownBy(() -> service.sendSync("topic", "tag", "msg"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("RocketMQ error");
        }
    }

    @Nested
    @DisplayName("sendAsync 异步发送")
    class SendAsyncTests {

        /**
         * 执行 {@code shouldCallAsyncSend} 操作。
         */
        @Test
        @DisplayName("应调用 asyncSend 异步发送")
        void shouldCallAsyncSend() {
            SendCallback callback = new SendCallback() {
                /** {@inheritDoc} */
                @Override
                public void onSuccess(SendResult sendResult) {}

                /** {@inheritDoc} */
                @Override
                public void onException(Throwable e) {}
            };

            service.sendAsync("topic", "tag", "msg", callback);

            verify(rocketMQTemplate).asyncSend(eq("topic:tag"), any(Message.class), eq(callback));
        }
    }

    @Nested
    @DisplayName("sendOneWay 单向发送")
    class SendOneWayTests {

        /**
         * 执行 {@code shouldCallSendOneWay} 操作。
         */
        @Test
        @DisplayName("应调用 sendOneWay 单向发送")
        void shouldCallSendOneWay() {
            service.sendOneWay("topic", "tag", "msg");

            verify(rocketMQTemplate).sendOneWay(eq("topic:tag"), any(Message.class));
        }
    }

    @Nested
    @DisplayName("sendDelay 延迟发送")
    class SendDelayTests {

        /**
         * 执行 {@code shouldSendWithDelayLevel} 操作。
         */
        @Test
        @DisplayName("应使用指定延迟等级同步发送")
        void shouldSendWithDelayLevel() {
            when(rocketMQTemplate.syncSend(anyString(), any(Message.class), anyLong(), anyInt()))
                    .thenReturn(sendResult);

            SendResult result = service.sendDelay("topic", "tag", "msg", 3);

            assertThat(result).isSameAs(sendResult);
            verify(rocketMQTemplate).syncSend(eq("topic:tag"), any(Message.class), eq(30000L), eq(3));
        }
    }

    @Nested
    @DisplayName("sendWithHeaders 带Header发送")
    class SendWithHeadersTests {

        /**
         * 执行 {@code shouldSendWithHeaders} 操作。
         */
        @Test
        @DisplayName("应附带Header键值对同步发送")
        void shouldSendWithHeaders() {
            when(rocketMQTemplate.syncSend(anyString(), any(Message.class))).thenReturn(sendResult);

            Map<String, Object> headers = Map.of("key1", "val1", "key2", 42);
            SendResult result = service.sendWithHeaders("topic", "tag", "msg", headers);

            assertThat(result).isSameAs(sendResult);
            verify(rocketMQTemplate).syncSend(eq("topic:tag"), any(Message.class));
        }

        /**
         * 执行 {@code shouldSendWithNullHeaders} 操作。
         */
        @Test
        @DisplayName("headers为null时应正常发送")
        void shouldSendWithNullHeaders() {
            when(rocketMQTemplate.syncSend(anyString(), any(Message.class))).thenReturn(sendResult);

            SendResult result = service.sendWithHeaders("topic", "tag", "msg", null);

            assertThat(result).isSameAs(sendResult);
        }
    }
}
