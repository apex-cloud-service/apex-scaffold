package com.apex.example.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.example.dto.CreateUserRequest;
import com.apex.scaffold.common.result.Result;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 用户API集成测试
 *
 * <p>测试完整的HTTP请求-响应流程,验证端到端功能。
 * 断言约定与主应用保持一致：
 * <ul>
 *   <li>成功响应：HTTP 200，{@code Result.code == 0}（{@code ResultCode.SUCCESS}）</li>
 *   <li>业务异常：HTTP 状态由 {@code HttpStatusUtil.fromBusinessCode} 映射
 *       （如 4001/4002 → 500），body 携带业务错误码</li>
 *   <li>参数校验失败：HTTP 400</li>
 *   <li>响应 data 因泛型擦除反序列化为 {@link Map}，按字段断言</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 */
@DisplayName("用户API集成测试")
class UserApiIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/example/users";

    /** 成功响应码（ResultCode.SUCCESS）。 */
    private static final int SUCCESS_CODE = 0;

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
                    assertThat(result.getCode()).isEqualTo(SUCCESS_CODE);
                    assertThat(result.getData()).isInstanceOf(Map.class);

                    Map<?, ?> data = (Map<?, ?>) result.getData();
                    assertThat(data.get("username")).isEqualTo("testuser");
                    assertThat(data.get("email")).isEqualTo("test@example.com");
                    assertThat(data.get("phone")).isEqualTo("138****8000"); // 手机号已脱敏
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

        // 尝试创建同名用户（USER_ALREADY_EXISTS=4002 → HTTP 500）
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
                .is5xxServerError()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(4002);
                    assertThat(result.getMsg()).contains("用户已存在");
                });
    }

    @Test
    @DisplayName("应该成功分页查询用户列表")
    void shouldListUsersWithPagination() {
        // 创建 3 个用户
        for (int i = 0; i < 3; i++) {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("page_user_" + i)
                    .email("page" + i + "@example.com")
                    .age(20 + i)
                    .phone("1380013810" + i)
                    .build();
            webTestClient
                    .post()
                    .uri(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus()
                    .isOk();
        }

        // 每页 2 条，查询第 1 页（data 为 PageResponse：list/total/pageNum/pageSize）
        webTestClient
                .get()
                .uri(BASE_URL + "?pageNum=1&pageSize=2")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(SUCCESS_CODE);

                    Map<String, Object> page = (Map<String, Object>) result.getData();
                    assertThat(page).containsKeys("list", "total", "pageNum", "pageSize");
                    assertThat(page.get("pageNum")).isEqualTo(1);
                    assertThat(page.get("pageSize")).isEqualTo(2);
                    assertThat(((Number) page.get("total")).longValue()).isGreaterThanOrEqualTo(3L);
                    assertThat(((java.util.List<?>) page.get("list")).size()).isLessThanOrEqualTo(2);
                });
    }

    @Test
    @DisplayName("非法分页参数应该返回参数错误")
    void shouldRejectInvalidPagination() {
        webTestClient
                .get()
                .uri(BASE_URL + "?pageNum=0&pageSize=1000")
                .exchange()
                .expectStatus()
                .is4xxClientError()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(400);
                });
    }

    @Test
    @DisplayName("应该成功查询用户列表（默认分页）")
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
                    assertThat(result.getCode()).isEqualTo(SUCCESS_CODE);
                    // 分页结构：data 为 PageResponse（list + total）
                    assertThat(result.getData()).isInstanceOf(Map.class);
                    Map<String, Object> page = (Map<String, Object>) result.getData();
                    assertThat(page).containsKeys("list", "total");
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

        Map<?, ?> created = (Map<?, ?>) createResult.getData();
        Number userId = (Number) created.get("id");

        // 查询该用户（data 为 Map，按字段断言）
        webTestClient
                .get()
                .uri(BASE_URL + "/" + userId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(SUCCESS_CODE);

                    Map<?, ?> data = (Map<?, ?>) result.getData();
                    assertThat(((Number) data.get("id")).longValue()).isEqualTo(userId.longValue());
                    assertThat(data.get("username")).isEqualTo("get_user_test");
                });
    }

    @Test
    @DisplayName("查询不存在的用户应该失败")
    void shouldFailWhenUserNotFound() {
        // USER_NOT_FOUND=4001 → HTTP 500
        webTestClient
                .get()
                .uri(BASE_URL + "/999999")
                .exchange()
                .expectStatus()
                .is5xxServerError()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getCode()).isEqualTo(4001);
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

        Map<?, ?> created = (Map<?, ?>) createResult.getData();
        Number userId = (Number) created.get("id");

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
                    assertThat(result.getCode()).isEqualTo(SUCCESS_CODE);

                    Map<?, ?> data = (Map<?, ?>) result.getData();
                    assertThat(data.get("email")).isEqualTo("new@example.com");
                    assertThat(((Number) data.get("age")).intValue()).isEqualTo(30);
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

        Map<?, ?> created = (Map<?, ?>) createResult.getData();
        Number userId = (Number) created.get("id");

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
                    assertThat(result.getCode()).isEqualTo(SUCCESS_CODE);
                    // 删除成功的业务文本在 data 字段（msg 为固定"成功"）
                    assertThat(result.getData()).isEqualTo("删除成功");
                });

        // 验证用户已被逻辑删除（查询返回 USER_NOT_FOUND=4001 → HTTP 500）
        webTestClient
                .get()
                .uri(BASE_URL + "/" + userId)
                .exchange()
                .expectStatus()
                .is5xxServerError()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(4001);
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
