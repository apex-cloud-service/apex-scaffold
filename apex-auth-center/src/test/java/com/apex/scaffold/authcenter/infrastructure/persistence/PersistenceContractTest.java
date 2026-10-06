package com.apex.scaffold.authcenter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.apex.scaffold.authcenter.infrastructure.persistence.po.PermissionPO;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.RolePO;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.UserRoleEntity;
import com.apex.scaffold.infrastructure.persistence.converter.UserRolePoConverter;
import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
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
    private static final String PREFIX = "com.apex.scaffold.authcenter.";

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
    void userRoleTenantIdIsMappedAcrossDomainAndPersistence() throws Exception {
        assertThat(UserRoleEntity.class.getDeclaredField("tenantId")).isNotNull();
        assertThat(UserRolePO.class.getDeclaredField("tenantId")).isNotNull();
        assertThat(UserRolePoConverter.class.getMethod("toPo", UserRoleEntity.class))
                .isNotNull();
        assertThat(UserRolePoConverter.class.getMethod("toEntity", UserRolePO.class))
                .isNotNull();
    }

    @Test
    void userWritesRejectPlaintextBeforeConversionOrDatabaseAccess() {
        com.apex.scaffold.authcenter.infrastructure.persistence.mapper.UserMapper mapper =
                mock(com.apex.scaffold.authcenter.infrastructure.persistence.mapper.UserMapper.class);
        com.apex.scaffold.authcenter.infrastructure.persistence.converter.UserConverter converter =
                mock(com.apex.scaffold.authcenter.infrastructure.persistence.converter.UserConverter.class);
        com.apex.scaffold.authcenter.infrastructure.persistence.repository.UserRepositoryImpl repository =
                new com.apex.scaffold.authcenter.infrastructure.persistence.repository.UserRepositoryImpl(
                        mapper, converter);
        com.apex.scaffold.authcenter.domain.model.entity.UserEntity user =
                new com.apex.scaffold.authcenter.domain.model.entity.UserEntity();
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
        com.apex.scaffold.authcenter.infrastructure.persistence.po.UserPO po =
                new com.apex.scaffold.authcenter.infrastructure.persistence.po.UserPO();
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
}
