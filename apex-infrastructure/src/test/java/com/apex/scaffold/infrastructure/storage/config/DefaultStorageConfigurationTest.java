package com.apex.scaffold.infrastructure.storage.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.domain.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link DefaultStorageConfiguration} 配置选择测试。
 *
 * <p>覆盖 MinIO、火山引擎 TOS 和本地存储三种默认类型，防止多实现同时存在时
 * 应用层再次出现 {@code NoUniqueBeanDefinitionException}。</p>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("默认对象存储选择")
class DefaultStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DefaultStorageConfiguration.class, StorageCandidates.class);

    /** 验证 Local 是缺省存储类型。 */
    @Test
    void shouldSelectLocalByDefault() {
        contextRunner.run(context ->
                assertThat(context.getBean(StorageService.class)).isSameAs(context.getBean("localStorageService")));
    }

    /** 验证显式选择 MinIO 时返回 MinIO 实现。 */
    @Test
    void shouldSelectMinio() {
        contextRunner.withPropertyValues("storage.default-type=minio").run(context -> assertThat(
                        context.getBean(StorageService.class))
                .isSameAs(context.getBean("minioStorageService")));
    }

    /** 验证显式选择 TOS 时返回 TOS 实现。 */
    @Test
    void shouldSelectVolcEngine() {
        contextRunner.withPropertyValues("storage.default-type=volcengine").run(context -> assertThat(
                        context.getBean(StorageService.class))
                .isSameAs(context.getBean("volcEngineStorageService")));
    }

    /** 验证显式选择本地存储时返回本地实现。 */
    @Test
    void shouldSelectLocalStorage() {
        contextRunner.withPropertyValues("storage.default-type=local").run(context -> assertThat(
                        context.getBean(StorageService.class))
                .isSameAs(context.getBean("localStorageService")));
    }

    /**
     * 测试使用的三个底层存储候选 Bean。
     *
     * @author Apex Team
     * @since 1.0.0
     */
    @Configuration(proxyBeanMethods = false)
    static class StorageCandidates {

        /**
         * 创建 MinIO 测试替身。
         *
         * @return MinIO 存储替身
         */
        @Bean("minioStorageService")
        StorageService minioStorageService() {
            return mock(StorageService.class);
        }

        /**
         * 创建 TOS 测试替身。
         *
         * @return TOS 存储替身
         */
        @Bean("volcEngineStorageService")
        StorageService volcEngineStorageService() {
            return mock(StorageService.class);
        }

        /**
         * 创建本地存储测试替身。
         *
         * @return 本地存储替身
         */
        @Bean("localStorageService")
        StorageService localStorageService() {
            return mock(StorageService.class);
        }
    }
}
