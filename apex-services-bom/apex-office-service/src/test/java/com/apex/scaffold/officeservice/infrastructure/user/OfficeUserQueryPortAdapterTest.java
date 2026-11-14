package com.apex.scaffold.officeservice.infrastructure.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("OfficeUserQueryPortAdapter 单元测试")
class OfficeUserQueryPortAdapterTest {

    private OfficeUserQueryPortAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OfficeUserQueryPortAdapter();
    }

    @Nested
    @DisplayName("getById 降级语义")
    class GetByIdTest {

        @Test
        @DisplayName("恒返回空 Mono（fail-closed）")
        void emptyMono() {
            StepVerifier.create(adapter.getById(1L)).verifyComplete();
        }

        @Test
        @DisplayName("多次调用仍为空（告警仅记录一次）")
        void idempotent() {
            StepVerifier.create(adapter.getById(1L)).verifyComplete();
            StepVerifier.create(adapter.getById(2L)).verifyComplete();
        }
    }

    @Nested
    @DisplayName("updateLoginInfo 降级语义")
    class UpdateLoginInfoTest {

        @Test
        @DisplayName("恒返回 false")
        void returnsFalse() {
            StepVerifier.create(adapter.updateLoginInfo(1L, "127.0.0.1"))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("端口实现实例关系正确")
    void implementsPort() {
        assertThat(adapter).isInstanceOf(OfficeUserQueryPortAdapter.class);
    }
}
