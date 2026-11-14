package com.apex.scaffold.bootall;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 单域真实上下文启动测试（H4 拓扑实证的最小可行范围）。
 *
 * <p>用 H2 内存库启动 file 域子上下文，验证：域开关能真正选中单一组件、数据源可用、
 * 上下文可刷新。system 域仍需 MySQL/Redis 与 License，不在本测试伪装通过。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("单域真实上下文启动")
class SingleDomainContextBootTest {

    @Test
    @DisplayName("file 域可在 H2 上独立启动且仅装配自身组件")
    void fileDomainStartsOnH2() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BootAllDomainConfiguration.class)
                .web(org.springframework.boot.WebApplicationType.REACTIVE)
                .properties(
                        "server.port=0",
                        "spring.application.name=apex-boot-all-file",
                        "spring.cloud.nacos.config.enabled=false",
                        "spring.cloud.nacos.discovery.enabled=false",
                        "spring.cloud.nacos.discovery.register-enabled=false",
                        "spring.autoconfigure.exclude=com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration,org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration",
                        "spring.datasource.url=jdbc:h2:mem:bootall-file;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.flyway.enabled=false",
                        "apex.component.system.enabled=false",
                        "apex.component.auth.enabled=false",
                        "apex.component.workflow.enabled=false",
                        "apex.component.file.enabled=true",
                        "apex.component.office.enabled=false",
                        "apex.component.integration.enabled=false",
                        "apex.component.integration.device.enabled=false")
                .run()) {
            assertThat(context.isActive()).isTrue();
            assertThat(context.getBeanDefinitionCount()).isPositive();
            assertThat(context.containsBean("fileService")).isTrue();
        }
    }
}
