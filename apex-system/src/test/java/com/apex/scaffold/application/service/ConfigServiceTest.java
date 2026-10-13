package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.assembler.ConfigAssembler;
import com.apex.scaffold.application.dto.admin.ConfigRequest;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.ConfigEntity;
import com.apex.scaffold.domain.repository.ConfigRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 参数配置应用服务单元测试。
 *
 * <p>覆盖参数配置的查询、创建、更新、删除与启停等核心业务规则，
 * 重点验证键名唯一性与内置参数禁止删除策略。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ConfigAssembler configAssembler;

    @InjectMocks
    private ConfigService configService;

    /** 用户自定义参数实体 */
    private ConfigEntity userConfig;

    /** 内置参数实体 */
    private ConfigEntity builtinConfig;

    /**
     * 初始化测试数据。
     */
    @BeforeEach
    void setUp() {
        userConfig = ConfigEntity.create("系统名称", "sys.site.name", "Apex Scaffold");
        userConfig.setId(1L);
        builtinConfig = ConfigEntity.create("默认分页", "sys.default.page", "20");
        builtinConfig.setId(2L);
        builtinConfig.setConfigType(ConfigEntity.ConfigType.BUILTIN.getCode());
    }

    /**
     * 测试创建参数配置成功。
     */
    @Test
    @DisplayName("createConfig - 成功创建参数配置")
    void createConfig_Success() {
        ConfigRequest request = new ConfigRequest();
        request.setConfigName("系统名称");
        request.setConfigKey("sys.site.name");
        request.setConfigValue("Apex Scaffold");

        when(configRepository.existsByKey("sys.site.name", null)).thenReturn(Mono.just(false));
        when(configRepository.insert(any(ConfigEntity.class))).thenReturn(Mono.just(1));

        configService
                .createConfig(request)
                .as(StepVerifier::create)
                .assertNext(config -> {
                    assertThat(config.getConfigKey()).isEqualTo("sys.site.name");
                    assertThat(config.isBuiltin()).isFalse();
                })
                .verifyComplete();

        verify(configRepository).insert(any(ConfigEntity.class));
    }

    /**
     * 测试创建重复键名的参数被拒绝。
     */
    @Test
    @DisplayName("createConfig - 键名已存在时抛出异常")
    void createConfig_DuplicateKeyFails() {
        ConfigRequest request = new ConfigRequest();
        request.setConfigName("系统名称");
        request.setConfigKey("sys.site.name");
        request.setConfigValue("Apex Scaffold");

        when(configRepository.existsByKey("sys.site.name", null)).thenReturn(Mono.just(true));

        configService
                .createConfig(request)
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(4722);
                })
                .verify();
    }

    /**
     * 测试按键名查询参数值。
     */
    @Test
    @DisplayName("getValueByKey - 成功按键名读取参数值")
    void getValueByKey_Success() {
        when(configRepository.findByKey("sys.site.name")).thenReturn(Mono.just(userConfig));

        configService
                .getValueByKey("sys.site.name")
                .as(StepVerifier::create)
                .expectNext("Apex Scaffold")
                .verifyComplete();
    }

    /**
     * 测试按键名查询不存在的参数返回空。
     */
    @Test
    @DisplayName("getValueByKey - 参数不存在时返回空")
    void getValueByKey_NotFound() {
        when(configRepository.findByKey("sys.unknown")).thenReturn(Mono.empty());

        configService.getValueByKey("sys.unknown").as(StepVerifier::create).verifyComplete();
    }

    /**
     * 测试删除内置参数被拒绝。
     */
    @Test
    @DisplayName("deleteConfig - 内置参数禁止删除")
    void deleteConfig_BuiltinFails() {
        when(configRepository.findById(2L)).thenReturn(Mono.just(builtinConfig));

        configService
                .deleteConfig(2L)
                .as(StepVerifier::create)
                .expectError(BusinessException.class)
                .verify();
    }

    /**
     * 测试删除用户自定义参数成功。
     */
    @Test
    @DisplayName("deleteConfig - 成功删除用户自定义参数")
    void deleteConfig_Success() {
        when(configRepository.findById(1L)).thenReturn(Mono.just(userConfig));
        when(configRepository.deleteById(1L)).thenReturn(Mono.just(1));

        configService.deleteConfig(1L).as(StepVerifier::create).expectNext(true).verifyComplete();
    }

    /**
     * 测试更新参数配置成功。
     */
    @Test
    @DisplayName("updateConfig - 成功更新参数值")
    void updateConfig_Success() {
        ConfigRequest request = new ConfigRequest();
        request.setConfigName("系统名称");
        request.setConfigKey("sys.site.name");
        request.setConfigValue("Apex Scaffold Pro");

        when(configRepository.findById(1L)).thenReturn(Mono.just(userConfig));
        when(configRepository.updateById(any(ConfigEntity.class))).thenReturn(Mono.just(1));

        configService
                .updateConfig(1L, request)
                .as(StepVerifier::create)
                .assertNext(config -> assertThat(config.getConfigValue()).isEqualTo("Apex Scaffold Pro"))
                .verifyComplete();
    }

    /**
     * 测试查询全部参数配置。
     */
    @Test
    @DisplayName("listConfigs - 成功查询全部参数配置")
    void listConfigs_Success() {
        when(configRepository.findAll()).thenReturn(Mono.just(List.of(userConfig, builtinConfig)));
        when(configAssembler.toDTOList(any())).thenAnswer(invocation -> {
            List<ConfigEntity> entities = invocation.getArgument(0);
            return entities.stream()
                    .map(e -> com.apex.scaffold.application.dto.admin.ConfigDTO.builder()
                            .id(e.getId())
                            .configKey(e.getConfigKey())
                            .configValue(e.getConfigValue())
                            .build())
                    .toList();
        });

        configService
                .listConfigs()
                .as(StepVerifier::create)
                .assertNext(configs -> assertThat(configs).hasSize(2))
                .verifyComplete();
    }

    /**
     * 测试启用停用参数配置。
     */
    @Test
    @DisplayName("updateConfigStatus - 成功停用参数")
    void updateConfigStatus_Success() {
        when(configRepository.findById(1L)).thenReturn(Mono.just(userConfig));
        when(configRepository.updateById(any(ConfigEntity.class))).thenReturn(Mono.just(1));

        configService
                .updateConfigStatus(1L, false)
                .as(StepVerifier::create)
                .assertNext(config -> assertThat(config.isEnabled()).isFalse())
                .verifyComplete();
    }
}
