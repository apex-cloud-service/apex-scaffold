package com.apex.scaffold.domain.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link StorageType} 的单元测试类
 *
 * <p>测试存储类型枚举的常量定义、name() 和 ordinal() 方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class StorageTypeTest {

    @Nested
    @DisplayName("枚举常量定义")
    class EnumConstantsTest {

        /**
         * 执行 {@code shouldHaveThreeConstants} 操作。
         */
        @Test
        @DisplayName("应包含 3 个枚举常量")
        void shouldHaveThreeConstants() {
            assertThat(StorageType.values()).hasSize(3);
        }

        /**
         * 执行 {@code shouldContainMinio} 操作。
         */
        @Test
        @DisplayName("应包含 MINIO 常量")
        void shouldContainMinio() {
            assertThat(StorageType.valueOf("MINIO")).isEqualTo(StorageType.MINIO);
        }

        /**
         * 执行 {@code shouldContainVolcengine} 操作。
         */
        @Test
        @DisplayName("应包含 VOLCENGINE 常量")
        void shouldContainVolcengine() {
            assertThat(StorageType.valueOf("VOLCENGINE")).isEqualTo(StorageType.VOLCENGINE);
        }

        /**
         * 执行 {@code shouldContainLocal} 操作。
         */
        @Test
        @DisplayName("应包含 LOCAL 常量")
        void shouldContainLocal() {
            assertThat(StorageType.valueOf("LOCAL")).isEqualTo(StorageType.LOCAL);
        }
    }

    @Nested
    @DisplayName("name() 方法")
    class NameMethodTest {

        /**
         * 执行 {@code minioNameShouldBeMINIO} 操作。
         */
        @Test
        @DisplayName("MINIO 的 name() 应返回 MINIO")
        void minioNameShouldBeMINIO() {
            assertThat(StorageType.MINIO.name()).isEqualTo("MINIO");
        }

        /**
         * 执行 {@code volcengineNameShouldBeVOLCENGINE} 操作。
         */
        @Test
        @DisplayName("VOLCENGINE 的 name() 应返回 VOLCENGINE")
        void volcengineNameShouldBeVOLCENGINE() {
            assertThat(StorageType.VOLCENGINE.name()).isEqualTo("VOLCENGINE");
        }

        /**
         * 执行 {@code localNameShouldBeLOCAL} 操作。
         */
        @Test
        @DisplayName("LOCAL 的 name() 应返回 LOCAL")
        void localNameShouldBeLOCAL() {
            assertThat(StorageType.LOCAL.name()).isEqualTo("LOCAL");
        }
    }

    @Nested
    @DisplayName("ordinal() 方法")
    class OrdinalMethodTest {

        /**
         * 执行 {@code minioOrdinalShouldBeZero} 操作。
         */
        @Test
        @DisplayName("MINIO 的 ordinal 应为 0")
        void minioOrdinalShouldBeZero() {
            assertThat(StorageType.MINIO.ordinal()).isZero();
        }

        /**
         * 执行 {@code volcengineOrdinalShouldBeOne} 操作。
         */
        @Test
        @DisplayName("VOLCENGINE 的 ordinal 应为 1")
        void volcengineOrdinalShouldBeOne() {
            assertThat(StorageType.VOLCENGINE.ordinal()).isEqualTo(1);
        }

        /**
         * 执行 {@code localOrdinalShouldBeTwo} 操作。
         */
        @Test
        @DisplayName("LOCAL 的 ordinal 应为 2")
        void localOrdinalShouldBeTwo() {
            assertThat(StorageType.LOCAL.ordinal()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("values() 方法")
    class ValuesMethodTest {

        /**
         * 执行 {@code valuesShouldReturnConstantsInDeclarationOrder} 操作。
         */
        @Test
        @DisplayName("values() 应返回按声明顺序排列的常量")
        void valuesShouldReturnConstantsInDeclarationOrder() {
            StorageType[] values = StorageType.values();
            assertThat(values[0]).isEqualTo(StorageType.MINIO);
            assertThat(values[1]).isEqualTo(StorageType.VOLCENGINE);
            assertThat(values[2]).isEqualTo(StorageType.LOCAL);
        }
    }
}
