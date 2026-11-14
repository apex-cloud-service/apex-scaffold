package com.apex.scaffold.integration.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.domain.service.DomainEventPublisher;
import com.apex.scaffold.integration.device.application.DeviceRegistryService;
import com.apex.scaffold.integration.device.application.DeviceTelemetryService;
import com.apex.scaffold.integration.device.autoconfigure.IotDeviceComponentAutoConfiguration;
import com.apex.scaffold.integration.device.autoconfigure.IotMqttAutoConfiguration;
import com.apex.scaffold.integration.device.domain.service.CommandDownlinkPort;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceCommandMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceRuleMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceShadowMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceTelemetryMapper;
import com.apex.scaffold.integration.device.interfaces.IotDeviceRouter;
import com.apex.scaffold.integration.device.mqtt.IotMqttSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.server.RouterFunction;

/**
 * 设备域自动装配测试（方案 M5 / 批次 E3）。
 *
 * <p>验证：MQTT 桥接默认关闭、classpath 缺 HiveMQ 客户端时整体裁剪为零 Bean
 * （optional 依赖安全）、显式启用后装配会话与下行端口；设备组件默认装配核心
 * 用例与 HTTP 接口，且 enabled / interfaces-enabled 开关生效。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("设备域自动装配测试")
class IotDeviceAutoConfigurationTest {

    private final ApplicationContextRunner mqttRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(IotMqttAutoConfiguration.class));

    private final ApplicationContextRunner componentRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IotDeviceComponentAutoConfiguration.class));

    @Test
    @DisplayName("MQTT：默认关闭，零 Bean")
    void mqttDisabledByDefault() {
        mqttRunner.run(context -> {
            assertThat(context).doesNotHaveBean(IotMqttSession.class);
            assertThat(context).doesNotHaveBean(CommandDownlinkPort.class);
        });
    }

    @Test
    @DisplayName("MQTT：classpath 缺 HiveMQ 客户端时整体裁剪为零 Bean")
    void mqttSkippedWithoutHivemqOnClasspath() {
        mqttRunner
                .withPropertyValues("apex.integration.iot.mqtt.enabled=true")
                .withClassLoader(new FilteredClassLoader(Mqtt3AsyncClient.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IotMqttSession.class);
                    assertThat(context).doesNotHaveBean(CommandDownlinkPort.class);
                });
    }

    @Test
    @DisplayName("MQTT：启用后装配会话与命令下行端口")
    void mqttAssemblesWhenEnabled() {
        mqttRunner
                .withPropertyValues(
                        "apex.integration.iot.mqtt.enabled=true",
                        "apex.integration.iot.mqtt.brokerUrl=tcp://127.0.0.1:1")
                .withBean(DeviceTelemetryService.class, () -> mock(DeviceTelemetryService.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(IotMqttSession.class);
                    assertThat(context).hasSingleBean(CommandDownlinkPort.class);
                });
    }

    @Test
    @DisplayName("组件：默认装配核心用例与 HTTP 接口")
    void componentAssemblesByDefault() {
        componentRunnerWithDependencies().run(context -> {
            assertThat(context).hasSingleBean(DeviceRegistryService.class);
            assertThat(context).hasSingleBean(DeviceTelemetryService.class);
            assertThat(context).hasSingleBean(IotDeviceRouter.class);
            assertThat(context).hasSingleBean(RouterFunction.class);
        });
    }

    @Test
    @DisplayName("组件：device.enabled=false 时不装配")
    void componentDisabledByProperty() {
        componentRunnerWithDependencies()
                .withPropertyValues("apex.component.integration.device.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DeviceRegistryService.class));
    }

    @Test
    @DisplayName("组件：interfaces-enabled=false 时仅装配核心，不暴露 HTTP 接口")
    void interfacesDisabledByProperty() {
        componentRunnerWithDependencies()
                .withPropertyValues("apex.component.integration.device.interfaces-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DeviceRegistryService.class);
                    assertThat(context).doesNotHaveBean(IotDeviceRouter.class);
                    assertThat(context).doesNotHaveBean(RouterFunction.class);
                });
    }

    /** 提供核心组件实例化所需的仓储 Mapper 与公共依赖（替代真实 MyBatis 环境）。 */
    private ApplicationContextRunner componentRunnerWithDependencies() {
        return componentRunner
                .withBean(DeviceMapper.class, () -> mock(DeviceMapper.class))
                .withBean(DeviceShadowMapper.class, () -> mock(DeviceShadowMapper.class))
                .withBean(DeviceTelemetryMapper.class, () -> mock(DeviceTelemetryMapper.class))
                .withBean(DeviceCommandMapper.class, () -> mock(DeviceCommandMapper.class))
                .withBean(DeviceRuleMapper.class, () -> mock(DeviceRuleMapper.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(DomainEventPublisher.class, () -> mock(DomainEventPublisher.class))
                .withBean(Validator.class, () -> Validation.buildDefaultValidatorFactory()
                        .getValidator());
    }
}
