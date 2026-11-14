package com.apex.scaffold.officeservice.interfaces.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

@DisplayName("MessageUtils 单元测试")
class MessageUtilsTest {

    private MessageSource messageSource;
    private MessageUtils messageUtils;

    @BeforeEach
    void setUp() {
        messageSource = mock(MessageSource.class);
        messageUtils = new MessageUtils(messageSource);
    }

    @Nested
    @DisplayName("响应式 get(code)")
    class ReactiveGetByCodeTest {

        @Test
        @DisplayName("使用 Context 中的 Locale 解析消息")
        void withContextLocale() {
            when(messageSource.getMessage(eq("sys.success"), isNull(), eq("sys.success"), eq(Locale.US)))
                    .thenReturn("Success");

            StepVerifier.create(messageUtils.get("sys.success").contextWrite(Context.of(Locale.class, Locale.US)))
                    .expectNext("Success")
                    .verifyComplete();
        }

        @Test
        @DisplayName("无 Locale 时使用简体中文默认值")
        void defaultLocale() {
            when(messageSource.getMessage(
                            eq("sys.success"), isNull(), eq("sys.success"), eq(Locale.SIMPLIFIED_CHINESE)))
                    .thenReturn("成功");

            StepVerifier.create(messageUtils.get("sys.success"))
                    .expectNext("成功")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("响应式 get(code, args)")
    class ReactiveGetWithArgsTest {

        @Test
        @DisplayName("带参数解析消息")
        void withArgs() {
            Object[] args = {"10"};
            when(messageSource.getMessage(eq("file.too.large"), eq(args), eq("file.too.large"), eq(Locale.US)))
                    .thenReturn("Size > 10MB");

            StepVerifier.create(
                            messageUtils.get("file.too.large", "10").contextWrite(Context.of(Locale.class, Locale.US)))
                    .expectNext("Size > 10MB")
                    .verifyComplete();
        }

        @Test
        @DisplayName("无 Locale 时带参数使用默认 Locale")
        void withArgsDefaultLocale() {
            Object[] args = {"5"};
            when(messageSource.getMessage(eq("k"), eq(args), eq("k"), eq(Locale.SIMPLIFIED_CHINESE)))
                    .thenReturn("m");

            StepVerifier.create(messageUtils.get("k", "5")).expectNext("m").verifyComplete();
        }
    }

    @Nested
    @DisplayName("同步 get(locale, code)")
    class SyncGetTest {

        @Test
        @DisplayName("同步解析无参数消息")
        void syncNoArgs() {
            when(messageSource.getMessage(eq("code"), isNull(), eq("code"), eq(Locale.US)))
                    .thenReturn("msg");

            assertThat(messageUtils.get(Locale.US, "code")).isEqualTo("msg");
            verify(messageSource).getMessage(eq("code"), isNull(), eq("code"), eq(Locale.US));
        }

        @Test
        @DisplayName("同步解析带参数消息")
        void syncWithArgs() {
            Object[] args = {"a", "b"};
            when(messageSource.getMessage(eq("code"), eq(args), eq("code"), eq(Locale.US)))
                    .thenReturn("msg");

            assertThat(messageUtils.get(Locale.US, "code", "a", "b")).isEqualTo("msg");
            verify(messageSource).getMessage(eq("code"), eq(args), eq("code"), eq(Locale.US));
        }
    }

    @Test
    @DisplayName("消息不存在时返回原始 key")
    void fallsBackToKey() {
        when(messageSource.getMessage(any(), any(), any(), any())).thenReturn("missing.key");

        assertThat(messageUtils.get(Locale.US, "missing.key")).isEqualTo("missing.key");
        StepVerifier.create(messageUtils.get("missing.key"))
                .expectNext("missing.key")
                .verifyComplete();
    }

    @Test
    @DisplayName("Mono 返回类型非空")
    void monoType() {
        when(messageSource.getMessage(eq("k"), isNull(), eq("k"), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenReturn("v");
        Mono<String> result = messageUtils.get("k");
        assertThat(result).isNotNull();
    }
}
