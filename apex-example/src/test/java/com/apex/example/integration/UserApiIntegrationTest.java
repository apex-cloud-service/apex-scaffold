package com.apex.example.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.example.dto.CreateUserRequest;
import com.apex.example.dto.UserResponse;
import com.apex.scaffold.common.result.Result;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 用户API集成测试
 *
 * <p>测试完整的HTTP请求-响应流程,验证端到端功能
 *
 * @author Apex Team
 * @version 1.0.0
 */
@DisplayName("用户API集成测试")
class UserApiIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/example/users";

    @Test
    @DisplayName("应该成功创建用户")
    void shouldCreateUserSuccessfully() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .age(25)
                .phone("13800138000")
                .build();

        webTestClient
                .post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(200);
                    assertThat(result.getData()).isInstanceOf(UserResponse.class);

                    UserResponse response = (UserResponse) result.getData();
                    assertThat(response.getUsername()).isEqualTo("testuser");
                    assertThat(response.getEmail()).isEqualTo("test@example.com");
                    assertThat(response.getPhone()).contains("****"); // 手机号已脱敏
                });
    }

    @Test
    @DisplayName("创建用户时用户名重复应该失败")
    void shouldFailWhenUsernameExists() {
        // 先创建一个用户
        CreateUserRequest request1 = CreateUserRequest.builder()
                .username("duplicate_user")
                .email("user1@example.com")
                .age(25)
                .phone("13800138001")
                .build();

        webTestClient
                .post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request1)
                .exchange()
                .expectStatus()
                .isOk();

        // 尝试创建同名用户
        CreateUserRequest request2 = CreateUserRequest.builder()
                .username("duplicate_user")
                .email("user2@example.com")
                .age(30)
                .phone("13800138002")
                .build();

        webTestClient
                .post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request2)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(500);
                    assertThat(result.getMsg()).contains("用户名已存在");
                });
    }

    @Test
    @DisplayName("应该成功查询用户列表")
    void shouldListUsersSuccessfully() {
        webTestClient
                .get()
                .uri(BASE_URL)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(200);
                    assertThat(result.getData()).isInstanceOf(List.class);
                });
    }

    @Test
    @DisplayName("应该成功查询用户详情")
    void shouldGetUserByIdSuccessfully() {
        // 先创建一个用户
        CreateUserRequest request = CreateUserRequest.builder()
                .username("get_user_test")
                .email("get@example.com")
                .age(28)
                .phone("13800138003")
                .build();

        Result createResult = webTestClient
                .post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .returnResult()
                .getResponseBody();

        Long userId = ((UserResponse) createResult.getData()).getId();

        // 查询该用户
        webTestClient
                .get()
                .uri(BASE_URL + "/" + userId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(200);

                    UserResponse response = (UserResponse) result.getData();
                    assertThat(response.getId()).isEqualTo(userId);
                    assertThat(response.getUsername()).isEqualTo("get_user_test");
                });
    }

    @Test
    @DisplayName("查询不存在的用户应该失败")
    void shouldFailWhenUserNotFound() {
        webTestClient
                .get()
                .uri(BASE_URL + "/999999")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(500);
                    assertThat(result.getMsg()).contains("用户不存在");
                });
    }

    @Test
    @DisplayName("应该成功更新用户")
    void shouldUpdateUserSuccessfully() {
        // 先创建一个用户
        CreateUserRequest createRequest = CreateUserRequest.builder()
                .username("update_user_test")
                .email("old@example.com")
                .age(25)
                .phone("13800138004")
                .build();

        Result createResult = webTestClient
                .post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .returnResult()
                .getResponseBody();

        Long userId = ((UserResponse) createResult.getData()).getId();

        // 更新用户
        CreateUserRequest updateRequest = CreateUserRequest.builder()
                .username("update_user_test")
                .email("new@example.com")
                .age(30)
                .phone("13800138005")
                .build();

        webTestClient
                .put()
                .uri(BASE_URL + "/" + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(200);

                    UserResponse response = (UserResponse) result.getData();
                    assertThat(response.getEmail()).isEqualTo("new@example.com");
                    assertThat(response.getAge()).isEqualTo(30);
                });
    }

    @Test
    @DisplayName("应该成功删除用户")
    void shouldDeleteUserSuccessfully() {
        // 先创建一个用户
        CreateUserRequest request = CreateUserRequest.builder()
                .username("delete_user_test")
                .email("delete@example.com")
                .age(25)
                .phone("13800138006")
                .build();

        Result createResult = webTestClient
                .post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .returnResult()
                .getResponseBody();

        Long userId = ((UserResponse) createResult.getData()).getId();

        // 删除用户
        webTestClient
                .delete()
                .uri(BASE_URL + "/" + userId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(200);
                    assertThat(result.getMsg()).contains("删除成功");
                });

        // 验证用户已被逻辑删除
        webTestClient
                .get()
                .uri(BASE_URL + "/" + userId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(500);
                });
    }

    @Test
    @DisplayName("参数校验失败应该返回错误")
    void shouldFailValidation() {
        CreateUserRequest invalidRequest = CreateUserRequest.builder()
                .username("ab") // 太短
                .email("invalid-email") // 格式错误
                .age(10) // 太小
                .phone("123") // 太短
                .build();

        webTestClient
                .post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}
