package com.apex.scaffold.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.apex.scaffold.auth.domain.model.OAuth2Authorization;
import com.apex.scaffold.auth.domain.repository.OAuth2AuthorizationPort;
import com.apex.scaffold.auth.domain.repository.OAuth2ConsentPort;
import com.apex.scaffold.auth.infrastructure.persistence.po.PermissionPO;
import com.apex.scaffold.auth.infrastructure.persistence.po.RolePO;
import com.apex.scaffold.auth.infrastructure.persistence.repository.OAuth2AuthorizationRepository;
import com.apex.scaffold.auth.infrastructure.persistence.repository.OAuth2ConsentRepository;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PersistenceContractTest {
    private static final String PREFIX = "com.apex.scaffold.auth.";

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"User", "Token", "Permission", "Role", "Session", "AuditLog"})
    void everyOperationRejectsMissingTenantBeforeDatabaseAccess(String name) throws Exception {
        Constructor<?> constructor = Class.forName(
                        PREFIX + "infrastructure.persistence.repository." + name + "RepositoryImpl")
                .getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
                .map(type -> mock(type))
                .toArray();
        Object repository = constructor.newInstance(dependencies);
        Class<?> contract = Class.forName(PREFIX + "domain.repository." + name + "Repository");
        for (Method method : contract.getMethods()) {
            // 统一认证收口（A3）：撤销流增量查询为显式平台级受控入口（无租户上下文执行），
            // 不适用"缺租户先拒绝"契约，其平台语义由 tokenRevocationPlatformFeedIsAccessible 单测覆盖。
            if ("findRevocationsSince".equals(method.getName())) {
                continue;
            }
            Object[] args = new Object[method.getParameterCount()];
            for (int index = 0; index < args.length; index++) {
                Class<?> type = method.getParameterTypes()[index];
                if (type == Long.class) {
                    args[index] = 1L;
                } else if (type == String.class) {
                    args[index] = "tenant-a";
                } else if (type == LocalDateTime.class) {
                    args[index] = LocalDateTime.now();
                } else if (type == Iterable.class) {
                    args[index] = List.of(1L);
                } else {
                    args[index] = type.getConstructor().newInstance();
                }
            }
            Publisher<?> result = (Publisher<?>) method.invoke(repository, args);
            StepVerifier.create(Flux.from(result))
                    .expectError(BusinessException.class)
                    .verify();
            verifyNoInteractions(dependencies);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Role", "Permission", "Session"})
    void numericLogicDeleteUsesSmallintLiterals(String name) throws Exception {
        Class<?> po = Class.forName(PREFIX + "infrastructure.persistence.po." + name + "PO");
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), name);
        assistant.setCurrentNamespace(name);
        TableInfo table = TableInfoHelper.initTableInfo(assistant, po);
        assertThat(table.isWithLogicDelete()).isTrue();
        assertThat(table.getLogicDeleteSql(false, true)).contains("deleted=0");
        assertThat(table.getLogicDeleteSql(false, false)).contains("deleted=1");
    }

    @Test
    void authorizationPOsReflectSchemaColumnNamesAndFields() throws Exception {
        var resourceField = PermissionPO.class.getDeclaredField("resourceIdentifier");
        assertThat(resourceField
                        .getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class)
                        .value())
                .isEqualTo("resource");
        assertThat(RolePO.class.getDeclaredField("dataScope")).isNotNull();
        assertThat(RolePO.class.getDeclaredField("tenantId")).isNotNull();
        assertThat(PermissionPO.class.getDeclaredField("tenantId")).isNotNull();
    }

    @Test
    void userWritesRejectPlaintextBeforeConversionOrDatabaseAccess() {
        com.apex.scaffold.auth.infrastructure.persistence.mapper.UserMapper mapper =
                mock(com.apex.scaffold.auth.infrastructure.persistence.mapper.UserMapper.class);
        com.apex.scaffold.auth.infrastructure.persistence.converter.UserConverter converter =
                mock(com.apex.scaffold.auth.infrastructure.persistence.converter.UserConverter.class);
        com.apex.scaffold.auth.infrastructure.persistence.repository.UserRepositoryImpl repository =
                new com.apex.scaffold.auth.infrastructure.persistence.repository.UserRepositoryImpl(mapper, converter);
        com.apex.scaffold.auth.domain.model.entity.UserEntity user =
                new com.apex.scaffold.auth.domain.model.entity.UserEntity();
        TenantContext.setTenantId("tenant-a");
        user.setTenantId("tenant-a");
        user.setId(1L);
        user.setPasswordHash("plaintext-secret");
        StepVerifier.create(repository.save(user))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(repository.update(user))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(mapper, converter);
        assertThat(user.toString()).doesNotContain("plaintext-secret");
        com.apex.scaffold.auth.infrastructure.persistence.po.UserPO po =
                new com.apex.scaffold.auth.infrastructure.persistence.po.UserPO();
        po.setPasswordHash("plaintext-secret");
        assertThat(po.toString()).doesNotContain("plaintext-secret");
    }

    @Test
    void domainDoesNotDependOnPersistenceOrFrameworks() {
        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(PREFIX + "domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(PREFIX + "infrastructure..", "com.baomidou..", "org.apache.ibatis..")
                .check(new ClassFileImporter().importPackages(PREFIX));
    }

    /**
     * 应用层只依赖领域契约：OAuth2 用例不得直接引用 auth 的 infrastructure / interfaces 实现。
     */
    @Test
    void applicationDoesNotDependOnInfrastructureOrInterfaces() {
        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(PREFIX + "application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(PREFIX + "infrastructure..", PREFIX + "interfaces..")
                .check(new ClassFileImporter().importPackages(PREFIX));
    }

    /**
     * OAuth2 适配器必须实现领域端口，且不得以 {@code @InterceptorIgnore} 绕过租户隔离。
     */
    @Test
    void oauth2AdaptersImplementDomainPortsWithoutTenantBypass() {
        assertThat(OAuth2AuthorizationPort.class).isAssignableFrom(OAuth2AuthorizationRepository.class);
        assertThat(OAuth2ConsentPort.class).isAssignableFrom(OAuth2ConsentRepository.class);
        for (Class<?> adapter : List.of(OAuth2AuthorizationRepository.class, OAuth2ConsentRepository.class)) {
            assertThat(adapter.getAnnotation(InterceptorIgnore.class)).isNull();
            for (Method method : adapter.getMethods()) {
                assertThat(method.getAnnotation(InterceptorIgnore.class))
                        .as("%s.%s", adapter.getSimpleName(), method.getName())
                        .isNull();
            }
        }
    }

    /** 授权记录适配器：缺租户一律拒绝，且不触达数据库；租户不匹配同样拒绝。 */
    @Test
    void oauth2AuthorizationAdapterRejectsUntrustedTenantBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OAuth2AuthorizationRepository repository = new OAuth2AuthorizationRepository(jdbcTemplate);
        LocalDateTime expiresAt = LocalDateTime.of(2030, 1, 1, 0, 0);
        List<Publisher<?>> operations = List.of(
                repository.saveAuthorizationCode(
                        null,
                        new OAuth2AuthorizationPort.OAuth2AuthorizationCodeCommand(
                                "1",
                                "client",
                                "user",
                                "read",
                                "https://cb",
                                "challenge",
                                "S256",
                                "code-hash",
                                expiresAt)),
                repository.saveTokenGrant(
                        null,
                        new OAuth2AuthorizationPort.OAuth2TokenGrantCommand(
                                "1",
                                "client",
                                "user",
                                "authorization_code",
                                "read",
                                "access-hash",
                                expiresAt,
                                "refresh-hash",
                                expiresAt)),
                repository.findByAuthorizationCode(null, "code-hash"),
                repository.findByAccessToken(null, "access-hash"),
                repository.findByRefreshToken(null, "refresh-hash"),
                repository.exchangeCode(
                        null,
                        new OAuth2AuthorizationPort.OAuth2CodeExchangeCommand(
                                "code-hash",
                                "client",
                                "https://cb",
                                "access-hash",
                                expiresAt,
                                "refresh-hash",
                                expiresAt)),
                repository.rotateRefresh(
                        null,
                        new OAuth2AuthorizationPort.OAuth2RefreshRotationCommand(
                                "client",
                                "old-refresh-hash",
                                "new-refresh-hash",
                                expiresAt,
                                "access-hash",
                                expiresAt,
                                "read")),
                repository.revokeByTokenHash(null, "access-hash", "client"));
        for (Publisher<?> operation : operations) {
            StepVerifier.create(Flux.from(operation))
                    .expectError(BusinessException.class)
                    .verify();
        }
        StepVerifier.create(repository
                        .findByAccessToken("tenant-b", "access-hash")
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(jdbcTemplate);
    }

    /** 同意适配器：缺租户一律拒绝，且不触达数据库。 */
    @Test
    void oauth2ConsentAdapterRejectsUntrustedTenantBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OAuth2ConsentRepository repository = new OAuth2ConsentRepository(jdbcTemplate);
        List<Publisher<?>> operations = List.of(
                repository.findActiveScope(null, "user", "client"),
                repository.save(null, "user", "client", "read"),
                repository.revoke(null, "user", "client"));
        for (Publisher<?> operation : operations) {
            StepVerifier.create(Flux.from(operation))
                    .expectError(BusinessException.class)
                    .verify();
        }
        verifyNoInteractions(jdbcTemplate);
    }

    /** 授权记录时间字段统一使用 {@link LocalDateTime}（UTC 语义），不得退化为毫秒时间戳。 */
    @Test
    void oauth2AuthorizationTimestampsUseLocalDateTime() {
        List<String> expiryComponents = Arrays.stream(OAuth2Authorization.class.getRecordComponents())
                .filter(component -> component.getName().endsWith("ExpiresAt"))
                .map(RecordComponent::getName)
                .toList();
        assertThat(expiryComponents)
                .containsExactly("authorizationCodeExpiresAt", "accessTokenExpiresAt", "refreshTokenExpiresAt");
        for (RecordComponent component : OAuth2Authorization.class.getRecordComponents()) {
            if (component.getName().endsWith("ExpiresAt")) {
                assertThat(component.getType()).isEqualTo(LocalDateTime.class);
            }
        }
    }

    /** 统一认证收口（A3）：撤销流平台级查询在有租户上下文缺失时仍可执行（受控平台入口语义）。 */
    @org.junit.jupiter.api.Test
    void tokenRevocationPlatformFeedIsAccessible() {
        var mapper =
                org.mockito.Mockito.mock(com.apex.scaffold.auth.infrastructure.persistence.mapper.TokenMapper.class);
        var converter = org.mockito.Mockito.mock(
                com.apex.scaffold.auth.infrastructure.persistence.converter.TokenConverter.class);
        var users = org.mockito.Mockito.mock(com.apex.scaffold.auth.infrastructure.persistence.mapper.UserMapper.class);
        var repository = new com.apex.scaffold.auth.infrastructure.persistence.repository.TokenRepositoryImpl(
                mapper, converter, users);
        var po = new com.apex.scaffold.auth.infrastructure.persistence.po.TokenPO();
        po.setJti("jti-platform-1");
        po.setRevokedAt(java.time.LocalDateTime.of(2026, 10, 31, 12, 0));
        org.mockito.Mockito.when(mapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(po));

        StepVerifier.create(repository
                        .findRevocationsSince(0L, 100)
                        .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("tenant-a")))
                .assertNext(revocation -> {
                    org.assertj.core.api.Assertions.assertThat(revocation.jti()).isEqualTo("jti-platform-1");
                    org.assertj.core.api.Assertions.assertThat(revocation.revokedAtMillis())
                            .isEqualTo(java.time.LocalDateTime.of(2026, 10, 31, 12, 0)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli());
                })
                .verifyComplete();
    }
}
