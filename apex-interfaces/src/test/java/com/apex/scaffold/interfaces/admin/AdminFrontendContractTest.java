package com.apex.scaffold.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.constant.SystemConstants;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 管理端前端资源（index.html、app.js、styles.css）与入口 Controller 的契约测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class AdminFrontendContractTest {

    private static final Pattern INLINE_EVENT_HANDLER = Pattern.compile("\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

    /**
     * 验证短入口 {@code /admin} 作为公共路径放行，且不放开 {@code /admin-api}
     * 前缀（后台接口仍受认证保护）。
     */
    @Test
    void shortEntryIsPublicWhileAdminApiStaysProtected() {
        assertThat(SystemConstants.PUBLIC_PATHS).contains("/admin", "/admin/**");
        assertThat(SystemConstants.PUBLIC_PATHS).doesNotContain("/admin-api/**", "/admin-api/v1/**");
    }

    /**
     * 验证静态外壳只引用外部样式与脚本，不含内联样式、内联脚本或内联事件处理器，
     * 并声明禁止搜索引擎收录的 robots 元标记。
     */
    @Test
    void staticShellUsesExternalAssetsAndHasNoInlineHandlers() throws IOException {
        String html = resource("static/admin/index.html");

        assertThat(html)
                .contains("href=\"/admin/styles.css\"")
                .contains("src=\"/admin/app.js\"")
                .contains("name=\"robots\" content=\"noindex,nofollow,noarchive\"")
                .doesNotContain("<style", "style=", "javascript:");
        assertThat(INLINE_EVENT_HANDLER.matcher(html).find()).isFalse();
    }

    /**
     * 验证管理端脚本只把 access token 保存在 sessionStorage，通过 credentials omit
     * 调用受保护的系统管理接口，且不使用 localStorage 或 refreshToken。
     */
    @Test
    void clientKeepsOnlyAccessTokenInTabSessionAndCallsProtectedSystemApis() throws IOException {
        String javascript = resource("static/admin/app.js");

        assertThat(javascript)
                .contains("sessionStorage")
                .contains("credentials: \"omit\"")
                .contains("/admin-api/v1/users")
                .contains("/admin-api/v1/menus")
                .contains("/admin-api/v1/dict/types")
                .contains("/admin-api/v1/configs")
                .contains("/admin-api/v1/jobs")
                .contains("/admin-api/v1/login-logs")
                .contains("/api/v1/auth/captcha")
                .contains("系统管理工作台")
                .contains("图形验证码")
                .contains("options.body instanceof FormData")
                .contains("payload.code !== 0")
                .doesNotContain("localStorage", "refreshToken");
    }

    /**
     * 验证系统管理控制台样式完整覆盖表格、卡片、弹窗与分页组件，
     * 且弹窗遮罩使用固定定位覆盖全屏。
     */
    @Test
    void systemConsoleStylesProvideCompleteComponentPresentation() throws IOException {
        String styles = resource("static/admin/styles.css");

        assertThat(styles)
                .contains(".main-content")
                .contains(".table {")
                .contains(".card-grid")
                .contains(".modal-backdrop { position: fixed")
                .contains(".modal {")
                .contains(".pagination")
                .contains(".badge")
                .contains(".captcha-image");
    }

    /**
     * 从 classpath 读取指定资源并以 UTF-8 返回文本。
     *
     * @param path classpath 资源路径
     * @return 资源文本内容
     * @throws IOException 资源读取失败时
     */
    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("classpath resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
