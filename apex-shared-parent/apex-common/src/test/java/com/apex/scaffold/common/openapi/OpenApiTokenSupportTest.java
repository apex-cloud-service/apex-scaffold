package com.apex.scaffold.common.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 开放应用令牌判定测试：必须同时满足路径、网关标记、应用声明，个人令牌与伪造组合一律拒绝。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("开放应用令牌判定")
class OpenApiTokenSupportTest {

    private Claims claims(Object... pairs) {
        var builder = io.jsonwebtoken.Jwts.claims();
        for (int index = 0; index < pairs.length; index += 2) {
            builder.add((String) pairs[index], pairs[index + 1]);
        }
        return builder.build();
    }

    @Test
    @DisplayName("完整应用令牌在开放路径应识别为应用")
    void applicationRecognized() {
        Claims claims = claims(
                "client_id", "partner-app",
                "tenant_id", "t-1",
                "token_use", "access",
                "grant_type", "client_credentials",
                "sub_uid", 42L);

        assertThat(OpenApiTokenSupport.isOpenApplication("/open-api/file/upload", "file-storage", claims))
                .isTrue();
        assertThat(OpenApiTokenSupport.subjectUserId(claims)).isEqualTo(42L);
        assertThat(OpenApiTokenSupport.tenantId(claims)).isEqualTo("t-1");
    }

    @Test
    @DisplayName("个人令牌即使带 client_id 也不得被识别为应用")
    void personalTokenRejected() {
        Claims claims = claims(
                "client_id", "partner-app",
                "tenant_id", "t-1",
                "token_use", "access",
                "grant_type", "client_credentials",
                "userId", 7L);

        assertThat(OpenApiTokenSupport.isOpenApplication("/open-api/file/upload", "file-storage", claims))
                .isFalse();
    }

    @Test
    @DisplayName("普通业务路径、缺网关标记、错误 token_use 或授权类型均拒绝")
    void guardsRejectIncompleteCombinations() {
        Claims valid = claims(
                "client_id", "partner-app",
                "tenant_id", "t-1",
                "token_use", "access",
                "grant_type", "client_credentials");

        assertThat(OpenApiTokenSupport.isOpenApplication("/api/v1/users", "file-storage", valid))
                .isFalse();
        assertThat(OpenApiTokenSupport.isOpenApplication("/open-api/file/upload", null, valid))
                .isFalse();
        assertThat(OpenApiTokenSupport.isOpenApplication("/open-api/file/upload", " ", valid))
                .isFalse();
        assertThat(OpenApiTokenSupport.isOpenApplication(
                        "/open-api/file/upload",
                        "file-storage",
                        claims(
                                "client_id",
                                "c",
                                "tenant_id",
                                "t",
                                "token_use",
                                "refresh",
                                "grant_type",
                                "client_credentials")))
                .isFalse();
        assertThat(OpenApiTokenSupport.isOpenApplication(
                        "/open-api/file/upload",
                        "file-storage",
                        claims("client_id", "c", "tenant_id", "t", "token_use", "access", "grant_type", "password")))
                .isFalse();
    }

    @Test
    @DisplayName("非法或缺失 sub_uid 返回 null，由写端点拒绝")
    void invalidSubjectReturnsNull() {
        assertThat(OpenApiTokenSupport.subjectUserId(claims("sub_uid", "abc"))).isNull();
        assertThat(OpenApiTokenSupport.subjectUserId(claims("sub_uid", 0L))).isNull();
        assertThat(OpenApiTokenSupport.subjectUserId(claims("client_id", "c"))).isNull();
    }
}
