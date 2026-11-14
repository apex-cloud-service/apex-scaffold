package com.apex.scaffold.fileservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.openapi.OpenApiTokenSupport;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 开放应用主体识别测试（文件服务视角）。
 *
 * <p>验证文件服务的开放通道路径与网关标记组合能被独立识别，且个人令牌与缺失标记的
 * 组合不会被误判为应用，从而避免应用令牌被当作普通用户或反之。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("文件服务开放应用识别")
class OpenApiApplicationIdentityTest {

    private io.jsonwebtoken.Claims application() {
        return Jwts.claims()
                .add("client_id", "partner-app")
                .add("tenant_id", "t-1")
                .add("token_use", "access")
                .add("grant_type", "client_credentials")
                .add("sub_uid", 42L)
                .build();
    }

    @Test
    @DisplayName("开放上传路径带网关标记时识别为应用并解析主体")
    void recognizesApplicationUpload() {
        var claims = application();

        assertThat(OpenApiTokenSupport.isOpenApplication("/open-api/file/upload", "file-storage", claims))
                .isTrue();
        assertThat(OpenApiTokenSupport.subjectUserId(claims)).isEqualTo(42L);
        assertThat(OpenApiTokenSupport.tenantId(claims)).isEqualTo("t-1");
    }

    @Test
    @DisplayName("管理端文件路径即使令牌相同也不识别为开放应用")
    void doesNotRecognizeManagementPath() {
        assertThat(OpenApiTokenSupport.isOpenApplication("/api/v1/file/upload", "file-storage", application()))
                .isFalse();
    }

    @Test
    @DisplayName("缺少网关标记的开放路径不被识别，交由后续安全链拒绝")
    void requiresGatewayMarker() {
        assertThat(OpenApiTokenSupport.isOpenApplication("/open-api/file/upload", null, application()))
                .isFalse();
    }

    @Test
    @DisplayName("个人令牌不能被识别为开放应用")
    void personalTokenIsNotApplication() {
        var personal = Jwts.claims()
                .add("userId", 7L)
                .add("tenantId", "t-1")
                .add("tokenType", "ACCESS")
                .build();

        assertThat(OpenApiTokenSupport.isOpenApplication("/open-api/file/upload", "file-storage", personal))
                .isFalse();
    }
}
