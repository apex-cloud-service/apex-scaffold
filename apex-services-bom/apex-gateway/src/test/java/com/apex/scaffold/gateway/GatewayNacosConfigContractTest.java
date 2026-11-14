package com.apex.scaffold.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 网关本地配置的 Nacos 环境开关契约，不启动客户端或加载远端配置.
 *
 * @author Apex Team
 */
class GatewayNacosConfigContractTest {

    @ParameterizedTest
    @CsvSource({
        "false,false,false",
        "false,false,true",
        "false,true,false",
        "false,true,true",
        "true,false,false",
        "true,false,true",
        "true,true,false",
        "true,true,true"
    })
    void prodHonorsIndependentComposeSwitches(boolean config, boolean discovery, boolean register) throws IOException {
        StandardEnvironment environment = environment(Map.of(
                "NACOS_CONFIG_ENABLED", Boolean.toString(config),
                "NACOS_DISCOVERY_ENABLED", Boolean.toString(discovery),
                "NACOS_REGISTER_ENABLED", Boolean.toString(register)));

        assertSwitches(environment, config, discovery, register);
    }

    @Test
    void prodKeepsNacosEnabledByDefault() throws IOException {
        assertSwitches(environment(Map.of()), true, true, true);
    }

    private static StandardEnvironment environment(Map<String, Object> variables) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("compose", variables));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String name : new String[] {"application-prod.yml", "application.yml", "bootstrap.yml"}) {
            for (PropertySource<?> source : loader.load(name, new ClassPathResource(name))) {
                environment.getPropertySources().addLast(source);
            }
        }
        return environment;
    }

    private static void assertSwitches(
            StandardEnvironment environment, boolean config, boolean discovery, boolean register) {
        assertThat(environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class))
                .as("config.enabled")
                .isEqualTo(config);
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class))
                .as("discovery.enabled")
                .isEqualTo(discovery);
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.register-enabled", Boolean.class))
                .as("discovery.register-enabled")
                .isEqualTo(register);
    }
}
