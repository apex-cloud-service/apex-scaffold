package com.apex.scaffold.interfaces.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

/**
 * Keycloak依赖验证测试
 * 验证Keycloak Admin Client依赖是否正常加载,无版本冲突
 */
class KeycloakDependencyTest {

    /**
     * 测试Keycloak Admin Client能否正常创建实例(不连接真实服务器)
     */
    @Test
    void shouldCreateKeycloakAdminClientInstance() {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl("http://localhost:8080")
                .realm("master")
                .clientId("admin-cli")
                .username("admin")
                .password("admin")
                .build();

        assertThat(keycloak).isNotNull();
    }

    /**
     * 测试Keycloak核心类能否正常加载
     */
    @Test
    void shouldLoadKeycloakCoreClasses() throws ClassNotFoundException {
        Class<?> keycloakClass = Class.forName("org.keycloak.admin.client.Keycloak");
        assertThat(keycloakClass).isNotNull();

        Class<?> keycloakBuilderClass = Class.forName("org.keycloak.admin.client.KeycloakBuilder");
        assertThat(keycloakBuilderClass).isNotNull();
    }

    /**
     * 测试Keycloak表示类能否正常加载
     */
    @Test
    void shouldLoadKeycloakRepresentationClasses() throws ClassNotFoundException {
        Class<?> userRepClass = Class.forName("org.keycloak.representations.idm.UserRepresentation");
        assertThat(userRepClass).isNotNull();

        Class<?> roleRepClass = Class.forName("org.keycloak.representations.idm.RoleRepresentation");
        assertThat(roleRepClass).isNotNull();

        Class<?> realmRepClass = Class.forName("org.keycloak.representations.idm.RealmRepresentation");
        assertThat(realmRepClass).isNotNull();

        Class<?> clientRepClass = Class.forName("org.keycloak.representations.idm.ClientRepresentation");
        assertThat(clientRepClass).isNotNull();
    }

    /**
     * 测试Keycloak资源接口能否正常加载
     */
    @Test
    void shouldLoadKeycloakResourceClasses() throws ClassNotFoundException {
        Class<?> realmResourceClass = Class.forName("org.keycloak.admin.client.resource.RealmResource");
        assertThat(realmResourceClass).isNotNull();

        Class<?> usersResourceClass = Class.forName("org.keycloak.admin.client.resource.UsersResource");
        assertThat(usersResourceClass).isNotNull();

        Class<?> rolesResourceClass = Class.forName("org.keycloak.admin.client.resource.RolesResource");
        assertThat(rolesResourceClass).isNotNull();
    }

    /**
     * 测试没有依赖冲突 - 所有核心类都能加载
     */
    @Test
    void shouldNotHaveDependencyConflicts() {
        try {
            // Admin Client
            Class.forName("org.keycloak.admin.client.Keycloak");
            Class.forName("org.keycloak.admin.client.KeycloakBuilder");

            // Representations
            Class.forName("org.keycloak.representations.idm.UserRepresentation");
            Class.forName("org.keycloak.representations.idm.RoleRepresentation");
            Class.forName("org.keycloak.representations.idm.RealmRepresentation");

            // Resources
            Class.forName("org.keycloak.admin.client.resource.RealmResource");
            Class.forName("org.keycloak.admin.client.resource.UsersResource");

        } catch (ClassNotFoundException e) {
            throw new AssertionError("Keycloak classes should be available without conflicts", e);
        }
    }

    /**
     * 测试Jackson依赖兼容性(Keycloak Admin Client依赖Jackson)
     */
    @Test
    void shouldHaveCompatibleJacksonDependencies() throws ClassNotFoundException {
        // Keycloak Admin Client需要Jackson来序列化/反序列化JSON
        Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
        Class.forName("com.fasterxml.jackson.core.JsonParser");
        Class.forName("com.fasterxml.jackson.annotation.JsonProperty");
    }
}
