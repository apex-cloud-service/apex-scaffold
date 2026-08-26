package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SystemMessageEntity} 的单元测试类
 *
 * <p>测试系统消息实体的 getter/setter 方法以及继承自 BaseEntity 的行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class SystemMessageEntityTest extends BaseEntityTest<SystemMessageEntity> {

    /** {@inheritDoc} */
    @Override
    protected SystemMessageEntity createEntity() {
        return new SystemMessageEntity();
    }

    @Nested
    @DisplayName("setter 和 getter 方法")
    class SetterGetterTest {

        /**
         * 设置{@code tersShouldUpdateFieldsAndGettersShouldReturnCorrectValues}。
         */
        @Test
        @DisplayName("使用 setter 设置属性后 getter 应正确返回")
        void settersShouldUpdateFieldsAndGettersShouldReturnCorrectValues() {
            SystemMessageEntity entity = new SystemMessageEntity();
            entity.setUserId(100L);
            entity.setMsgType("NOTICE");
            entity.setContent("您的照片已通过审核");
            entity.setRefType("PHOTO");
            entity.setRefId(200L);
            LocalDateTime readAt = LocalDateTime.now();
            entity.setReadAt(readAt);

            assertThat(entity.getUserId()).isEqualTo(100L);
            assertThat(entity.getMsgType()).isEqualTo("NOTICE");
            assertThat(entity.getContent()).isEqualTo("您的照片已通过审核");
            assertThat(entity.getRefType()).isEqualTo("PHOTO");
            assertThat(entity.getRefId()).isEqualTo(200L);
            assertThat(entity.getReadAt()).isEqualTo(readAt);
        }

        /**
         * 执行 {@code newEntityShouldHaveNullFields} 操作。
         */
        @Test
        @DisplayName("新创建的实体字段应为 null")
        void newEntityShouldHaveNullFields() {
            SystemMessageEntity entity = new SystemMessageEntity();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getMsgType()).isNull();
            assertThat(entity.getContent()).isNull();
            assertThat(entity.getRefType()).isNull();
            assertThat(entity.getRefId()).isNull();
            assertThat(entity.getReadAt()).isNull();
        }
    }

    @Nested
    @DisplayName("业务场景")
    class BusinessScenarioTest {

        /**
         * 执行 {@code unreadMessageShouldHaveNullReadAt} 操作。
         */
        @Test
        @DisplayName("未读系统消息应有 null 的 readAt")
        void unreadMessageShouldHaveNullReadAt() {
            SystemMessageEntity entity = new SystemMessageEntity();
            entity.setUserId(1L);
            entity.setMsgType("NOTICE");
            entity.setContent("新版本上线通知");

            assertThat(entity.getReadAt()).isNull();
        }

        /**
         * 执行 {@code readMessageShouldHaveReadAt} 操作。
         */
        @Test
        @DisplayName("已读系统消息应记录 readAt")
        void readMessageShouldHaveReadAt() {
            SystemMessageEntity entity = new SystemMessageEntity();
            LocalDateTime now = LocalDateTime.now();
            entity.setUserId(1L);
            entity.setReadAt(now);

            assertThat(entity.getReadAt()).isEqualTo(now);
        }
    }
}
