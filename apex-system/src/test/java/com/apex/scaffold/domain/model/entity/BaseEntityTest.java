package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 实体测试基类，提供通用的实体行为测试方法。
 *
 * <p>测试 {@link BaseEntity} 的核心业务方法：isNew、markAsDeleted、restore、initTimestamps、touch。
 * 子类通过实现 {@link #createEntity()} 提供具体实体实例。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@DisplayName("BaseEntity 行为测试")
public abstract class BaseEntityTest<T extends BaseEntity> {
    protected abstract T createEntity();

    @Nested
    @DisplayName("isNew 方法")
    class IsNewTests {
        @Test
        @DisplayName("新建实体（id为null）应返回true")
        void shouldReturnTrueWhenIdIsNull() {
            T entity = createEntity();
            assertThat(entity.isNew()).isTrue();
        }

        @Test
        @DisplayName("已持久化实体（id不为null）应返回false")
        void shouldReturnFalseWhenIdIsSet() {
            T entity = createEntity();
            entity.setId(100L);
            assertThat(entity.isNew()).isFalse();
        }
    }

    @Nested
    @DisplayName("markAsDeleted 方法")
    class MarkAsDeletedTests {
        @Test
        @DisplayName("标记删除后 isDeleted 应返回true")
        void shouldReturnTrueAfterMarkAsDeleted() {
            T entity = createEntity();
            entity.markAsDeleted();
            assertThat(entity.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("标记删除后应更新 updateTime")
        void shouldTouchUpdateTimeAfterMarkAsDeleted() {
            T entity = createEntity();
            entity.initTimestamps();
            LocalDateTime before = entity.getUpdateTime();
            entity.markAsDeleted();
            assertThat(entity.getUpdateTime()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("restore 方法")
    class RestoreTests {
        @Test
        @DisplayName("恢复后 isDeleted 应返回false")
        void shouldReturnFalseAfterRestore() {
            T entity = createEntity();
            entity.markAsDeleted();
            entity.restore();
            assertThat(entity.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("恢复后应更新 updateTime")
        void shouldTouchUpdateTimeAfterRestore() {
            T entity = createEntity();
            entity.initTimestamps();
            entity.markAsDeleted();
            LocalDateTime before = entity.getUpdateTime();
            entity.restore();
            assertThat(entity.getUpdateTime()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("initTimestamps 方法")
    class InitTimestampsTests {
        @Test
        @DisplayName("初始化后 createTime 和 updateTime 应不为 null")
        void shouldInitializeTimestamps() {
            T entity = createEntity();
            entity.initTimestamps();
            assertThat(entity.getCreateTime()).isNotNull();
            assertThat(entity.getUpdateTime()).isNotNull();
        }

        @Test
        @DisplayName("初始化后 createTime 和 updateTime 应相等")
        void shouldSetEqualTimestampsOnInit() {
            T entity = createEntity();
            entity.initTimestamps();
            assertThat(entity.getCreateTime()).isEqualTo(entity.getUpdateTime());
        }
    }
}
