package com.apex.scaffold.application.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.apex.scaffold.domain.event.UserRegisteredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link UserRegisteredEventListener} 的单元测试类
 *
 * <p>测试用户注册事件监听器的核心逻辑：事件处理方法不应抛出异常。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("UserRegisteredEventListener 单元测试")
class UserRegisteredEventListenerTest {

    /**
     * 创建不注入任何依赖的监听器实例
     */
    private UserRegisteredEventListener createEmptyListener() {
        return new UserRegisteredEventListener(java.util.Optional.empty(), java.util.Optional.empty());
    }

    @Test
    @DisplayName("处理事件不应抛出异常")
    void shouldHandleEventWithoutException() {
        UserRegisteredEventListener listener = createEmptyListener();
        UserRegisteredEvent event = new UserRegisteredEvent(1L, "测试用户", "13800138000");

        assertThatCode(() -> listener.handleUserRegisteredEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("事件应正确包含用户注册信息")
    void eventShouldContainCorrectUserInfo() {
        UserRegisteredEvent event = new UserRegisteredEvent(2L, "张三", "13900139000");

        assertThat(event.getUserId()).isEqualTo(2L);
        assertThat(event.getNickName()).isEqualTo("张三");
        assertThat(event.getPhone()).isEqualTo("13900139000");
    }

    @Test
    @DisplayName("处理null事件应安全忽略")
    void shouldHandleNullEvent() {
        UserRegisteredEventListener listener = createEmptyListener();

        assertThatCode(() -> listener.handleUserRegisteredEvent(null)).doesNotThrowAnyException();
    }
}
