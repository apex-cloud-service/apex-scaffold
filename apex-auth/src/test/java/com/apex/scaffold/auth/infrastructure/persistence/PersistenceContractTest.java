package com.apex.scaffold.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.apex.scaffold.auth.infrastructure.persistence.po.PermissionPO;
import com.apex.scaffold.auth.infrastructure.persistence.po.RolePO;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
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
