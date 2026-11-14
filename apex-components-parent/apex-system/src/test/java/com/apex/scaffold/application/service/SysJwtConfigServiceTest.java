package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.assembler.SysJwtConfigAssembler;
import com.apex.scaffold.application.dto.admin.JwtConfigDTO;
import com.apex.scaffold.application.dto.admin.JwtTestRequest;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.SysJwtConfigEntity;
import com.apex.scaffold.domain.repository.SysJwtConfigRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link SysJwtConfigService} JWT 配置与测试功能单元测试。
 *
 * <p>验证 JWT 配置查询与测试 Token 生成/验证流程，外部依赖
 * （仓储、装配器、事件发布器）全部 Mock，不依赖真实数据库与密钥配置。
 *
 * <h2>测试覆盖</h2>
 * <ul>
 *   <li>testJwt - HS256 算法成功生成并验证 Token（含自定义载荷回读）</li>
 *   <li>testJwt - 不支持的签名算法（RS256）返回 isValid=false 的降级响应</li>
 *   <li>testJwt - 租户 JWT 配置缺失时抛出业务异常</li>
 *   <li>租户上下文未初始化时拒绝操作</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SysJwtConfigService - JWT 配置与测试功能")
class SysJwtConfigServiceTest {

    private static final String TENANT = "tenant-a";

    @Mock
    private SysJwtConfigRepository jwtConfigRepository;

    @Mock
    private SysJwtConfigAssembler jwtConfigAssembler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private JwtConfigDTO jwtConfig;

    private SysJwtConfigService service;

    @BeforeEach
    void setUp() {
        service = new SysJwtConfigService(jwtConfigRepository, jwtConfigAssembler, eventPublisher);
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 构建 HS256 配置的测试请求。
     */
    private JwtTestRequest testRequest() {
        return JwtTestRequest.builder()
                .payload(Map.of("sub", "user-001"))
                .expiresIn(600L)
                .build();
    }

    /**
     * 打桩：租户配置查询返回指定算法的 JWT 配置（仅打桩到算法判定为止的依赖）。
     */
    private void stubTenantConfig(String algorithm) {
        SysJwtConfigEntity entity = new SysJwtConfigEntity();
        when(jwtConfigRepository.findByTenantId(TENANT)).thenReturn(Mono.just(entity));
        when(jwtConfigAssembler.toDTO(entity)).thenReturn(jwtConfig);
        when(jwtConfig.getAlgorithm()).thenReturn(algorithm);
    }

    @Test
    @DisplayName("testJwt - HS256 成功生成并验证 Token，载荷可回读")
    void testJwt_HS256_Success() {
        stubTenantConfig("HS256");
        // HS256 要求密钥长度建议 ≥ 256 位（32 字节）
        when(jwtConfig.getSecret()).thenReturn("0123456789abcdef0123456789abcdef");
        when(jwtConfig.getIssuer()).thenReturn("apex-test");

        StepVerifier.create(service.testJwt(testRequest()))
                .assertNext(response -> {
                    assertThat(response.getIsValid()).isTrue();
                    assertThat(response.getToken()).isNotBlank();
                    assertThat(response.getIssuer()).isEqualTo("apex-test");
                    assertThat(response.getIssuedAt()).isNotNull();
                    assertThat(response.getExpiresAt()).isAfter(response.getIssuedAt());
                    // 自定义载荷在解码结果中可回读
                    assertThat(response.getDecoded()).containsEntry("sub", "user-001");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("testJwt - 不支持的算法（RS256）返回 isValid=false 的降级响应")
    void testJwt_UnsupportedAlgorithm() {
        stubTenantConfig("RS256");

        StepVerifier.create(service.testJwt(testRequest()))
                .assertNext(response -> {
                    assertThat(response.getIsValid()).isFalse();
                    assertThat(response.getToken()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("testJwt - 租户 JWT 配置缺失时抛出业务异常")
    void testJwt_ConfigMissing() {
        when(jwtConfigRepository.findByTenantId(TENANT)).thenReturn(Mono.empty());

        StepVerifier.create(service.testJwt(testRequest()))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("租户上下文未初始化时拒绝测试请求")
    void rejectsWhenTenantMissing() {
        TenantContext.clear();

        // 租户校验在服务入口急切执行，未初始化时在调用点即抛出
        assertThatThrownBy(() -> service.testJwt(testRequest())).isInstanceOf(BusinessException.class);
    }
}
