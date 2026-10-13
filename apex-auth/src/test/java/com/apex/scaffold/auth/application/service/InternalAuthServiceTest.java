package com.apex.scaffold.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.model.entity.UserEntity;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.JwtKeyMaterialService;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.contract.dto.internal.TokenValidationDTO;
import com.apex.scaffold.contract.dto.internal.TokenValidationRequest;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link InternalAuthService} 单元测试（迁移方案 Phase 2.3）.
 *
 * <p>覆盖内部契约两个接口的核心语义：
 * <ul>
 *   <li>权限查询：账户校验、活跃过滤、租户类型桥接、异常降级为空权限；</li>
 *   <li>Token 校验：缺失/有效/租户不一致/过期/撤销/签名异常的原因码映射。</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.1
 */
class InternalAuthServiceTest {

    private UserRepository userRepository;
    private UnifiedAuthorizationQueryService authorizationQueryService;
    private TokenService tokenService;
    private JwtKeyMaterialService jwtKeyMaterialService;
    private InternalAuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authorizationQueryService = mock(UnifiedAuthorizationQueryService.class);
        tokenService = mock(TokenService.class);
        jwtKeyMaterialService = mock(JwtKeyMaterialService.class);
        service =
                new InternalAuthService(userRepository, authorizationQueryService, tokenService, jwtKeyMaterialService);
    }

    @Nested
    class 权限查询 {

        @Test
        void 活跃账户返回权限码列表() {
            UserEntity user = UserEntity.builder()
                    .id(1L)
                    .tenantId("100")
                    .accountId(1001L)
                    .status(1)
                    .deleted(0)
                    .build();
            var snapshot = new UnifiedAuthorizationQueryService.Authorization(
                    "100", 1L, List.of("sys:user:view", "sys:user:edit"), List.of());
            when(userRepository.findByAccountId(1001L, "100")).thenReturn(Mono.just(user));
            when(authorizationQueryService.query("100", 1L)).thenReturn(Mono.just(snapshot));

            StepVerifier.create(service.getUserPermissions(1001L, 100L))
                    .assertNext(
                            permissions -> assertThat(permissions).containsExactly("sys:user:view", "sys:user:edit"))
                    .verifyComplete();
            verify(authorizationQueryService).query("100", 1L);
        }

        @Test
        void 锁定账户返回空权限列表() {
            UserEntity user = UserEntity.builder()
                    .id(1L)
                    .tenantId("100")
                    .accountId(1001L)
                    .status(2)
                    .lockedUntil(LocalDateTime.now().plusMinutes(30))
                    .deleted(0)
                    .build();
            when(userRepository.findByAccountId(1001L, "100")).thenReturn(Mono.just(user));

            StepVerifier.create(service.getUserPermissions(1001L, 100L))
                    .assertNext(permissions -> assertThat(permissions).isEmpty())
                    .verifyComplete();
            verifyNoInteractions(authorizationQueryService);
        }

        @Test
        void 非法账户ID直接返回空权限() {
            StepVerifier.create(service.getUserPermissions(0L, 100L))
                    .assertNext(permissions -> assertThat(permissions).isEmpty())
                    .verifyComplete();
            StepVerifier.create(service.getUserPermissions(null, 100L))
                    .assertNext(permissions -> assertThat(permissions).isEmpty())
                    .verifyComplete();
            verifyNoInteractions(userRepository, authorizationQueryService);
        }

        @Test
        void 空租户直接返回空权限() {
            StepVerifier.create(service.getUserPermissions(1001L, null))
                    .assertNext(permissions -> assertThat(permissions).isEmpty())
                    .verifyComplete();
            verifyNoInteractions(userRepository);
        }

        @Test
        void 仓储异常降级为空权限() {
            when(userRepository.findByAccountId(1001L, "100")).thenReturn(Mono.error(new BusinessException("数据库不可用")));

            StepVerifier.create(service.getUserPermissions(1001L, 100L))
                    .assertNext(permissions -> assertThat(permissions).isEmpty())
                    .verifyComplete();
        }
    }

    @Nested
    class Token校验 {

        @Test
        void 缺失Token返回MISSING_TOKEN() {
            StepVerifier.create(service.validateToken(null))
                    .assertNext(dto -> {
                        assertThat(dto.getValid()).isFalse();
                        assertThat(dto.getReason()).isEqualTo("MISSING_TOKEN");
                    })
                    .verifyComplete();

            StepVerifier.create(service.validateToken(
                            TokenValidationRequest.builder().token("  ").build()))
                    .assertNext(dto -> assertThat(dto.getReason()).isEqualTo("MISSING_TOKEN"))
                    .verifyComplete();
            verifyNoInteractions(tokenService, jwtKeyMaterialService);
        }

        @Test
        void 有效Token返回富化快照() {
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
            TokenEntity entity = TokenEntity.builder()
                    .tenantId("100")
                    .userId(1L)
                    .tokenType("ACCESS")
                    .expiresAt(expiresAt)
                    .revoked(0)
                    .build();
            Claims claims = mock(Claims.class);
            when(claims.get("accountId", Long.class)).thenReturn(1001L);
            when(claims.get("clientType", String.class)).thenReturn("WEB");
            when(tokenService.validateToken("raw-token")).thenReturn(Mono.just(entity));
            when(jwtKeyMaterialService.parse("raw-token")).thenReturn(Mono.just(claims));

            StepVerifier.create(service.validateToken(TokenValidationRequest.builder()
                            .token("raw-token")
                            .tenantId(100L)
                            .source("system-service")
                            .build()))
                    .assertNext(dto -> {
                        assertThat(dto.getValid()).isTrue();
                        assertThat(dto.getAccountId()).isEqualTo(1001L);
                        assertThat(dto.getTenantId()).isEqualTo(100L);
                        assertThat(dto.getClientType()).isEqualTo("WEB");
                        assertThat(dto.getExpiresAt()).isEqualTo(expiresAt);
                    })
                    .verifyComplete();
        }

        @Test
        void 租户不一致返回TENANT_MISMATCH() {
            TokenEntity entity = TokenEntity.builder()
                    .tenantId("default")
                    .userId(1L)
                    .tokenType("ACCESS")
                    .build();
            Claims claims = mock(Claims.class);
            when(tokenService.validateToken("raw-token")).thenReturn(Mono.just(entity));
            when(jwtKeyMaterialService.parse("raw-token")).thenReturn(Mono.just(claims));

            StepVerifier.create(service.validateToken(TokenValidationRequest.builder()
                            .token("raw-token")
                            .tenantId(100L)
                            .build()))
                    .assertNext(dto -> {
                        assertThat(dto.getValid()).isFalse();
                        assertThat(dto.getReason()).isEqualTo("TENANT_MISMATCH");
                    })
                    .verifyComplete();
        }

        @Test
        void 过期Token映射为EXPIRED() {
            when(tokenService.validateToken("raw-token")).thenReturn(Mono.error(new BusinessException("令牌已过期")));

            StepVerifier.create(service.validateToken(
                            TokenValidationRequest.builder().token("raw-token").build()))
                    .assertNext(dto -> {
                        assertThat(dto.getValid()).isFalse();
                        assertThat(dto.getReason()).isEqualTo("EXPIRED");
                    })
                    .verifyComplete();
        }

        @Test
        void 撤销Token映射为REVOKED() {
            when(tokenService.validateToken("raw-token")).thenReturn(Mono.error(new BusinessException("令牌已被撤销")));

            StepVerifier.create(service.validateToken(
                            TokenValidationRequest.builder().token("raw-token").build()))
                    .assertNext(dto -> assertThat(dto.getReason()).isEqualTo("REVOKED"))
                    .verifyComplete();
        }

        @Test
        void 令牌不存在映射为TOKEN_NOT_FOUND() {
            when(tokenService.validateToken("raw-token")).thenReturn(Mono.error(new BusinessException("令牌不存在")));

            StepVerifier.create(service.validateToken(
                            TokenValidationRequest.builder().token("raw-token").build()))
                    .assertNext(dto -> assertThat(dto.getReason()).isEqualTo("TOKEN_NOT_FOUND"))
                    .verifyComplete();
        }

        @Test
        void 非业务异常映射为INVALID_SIGNATURE() {
            when(tokenService.validateToken("raw-token")).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(service.validateToken(
                            TokenValidationRequest.builder().token("raw-token").build()))
                    .assertNext(dto -> {
                        assertThat(dto.getValid()).isFalse();
                        assertThat(dto.getReason()).isEqualTo("INVALID_SIGNATURE");
                    })
                    .verifyComplete();
        }
    }

    @Test
    void 非数字租户编码出参回填为null() {
        TokenEntity entity = TokenEntity.builder()
                .tenantId("default")
                .userId(1L)
                .tokenType("ACCESS")
                .build();
        Claims claims = mock(Claims.class);
        when(claims.get("accountId", Long.class)).thenReturn(1001L);
        when(tokenService.validateToken("raw-token")).thenReturn(Mono.just(entity));
        when(jwtKeyMaterialService.parse("raw-token")).thenReturn(Mono.just(claims));

        StepVerifier.create(service.validateToken(
                        TokenValidationRequest.builder().token("raw-token").build()))
                .assertNext((TokenValidationDTO dto) -> {
                    assertThat(dto.getValid()).isTrue();
                    assertThat(dto.getTenantId()).isNull();
                })
                .verifyComplete();
    }
}
