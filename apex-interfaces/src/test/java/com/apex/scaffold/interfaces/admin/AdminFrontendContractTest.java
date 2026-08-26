package com.apex.scaffold.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.constant.SystemConstants;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.result.view.RedirectView;

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
     * 验证短入口 {@code /admin} 重定向到内嵌应用的 index.html，且公共路径仅放行
     * 该短入口而不放行 {@code /admin-api} 前缀。
     */
    @Test
    void shortEntryRedirectsToEmbeddedApplication() {
        RedirectView view = new AdminFrontendController().index();

        assertThat(view.getUrl()).isEqualTo("/admin/index.html");
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
     * 调用受保护的审核与用户治理接口，且不使用 localStorage、innerHTML 或 refreshToken。
     */
    @Test
    void clientKeepsOnlyAccessTokenInTabSessionAndCallsProtectedReviewApis() throws IOException {
        String javascript = resource("static/admin/app.js");

        assertThat(javascript)
                .contains("sessionStorage")
                .contains("credentials: \"omit\"")
                .contains("/admin-api/v1/photo-reviews")
                .contains("/admin-api/v1/interest-reviews")
                .contains("/admin-api/v1/users")
                .contains("/admin-api/v1/high-risk-users")
                .contains("百度审核原因")
                .contains("命中原因")
                .contains("phoneRegistrationAllowed")
                .contains("wechatRegistrationAllowed")
                .contains("/lifestyle-photos")
                .contains("options.body instanceof FormData")
                .contains("clipAesthetic?.analysisStatus !== \"DONE\"")
                .contains("upload-progress-fill")
                .contains("payload.code !== 0")
                .doesNotContain("localStorage", "innerHTML", "refreshToken");
    }

    /**
     * 验证审核与生活照预览样式完整展示图片（object-fit: contain），且过期提示与
     * 删除按钮使用静态定位，不被其他元素遮挡。
     */
    @Test
    void reviewAndLifestylePhotosUseCompleteUnobstructedPresentation() throws IOException {
        String styles = resource("static/admin/styles.css");

        assertThat(styles)
                .contains(".preview-frame img")
                .contains("object-fit: contain")
                .contains(".preview-expiry { position: static")
                .contains(".user-photo img { width: 100%; height: 100%; max-height: 360px; object-fit: contain")
                .contains(".user-photo-delete { position: static");
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
