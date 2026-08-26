package com.apex.scaffold.application.dto.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.domain.model.admin.ReviewReason;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 后台管理 DTO 雪花 ID 字符串序列化契约测试。
 *
 * <p>验证超过 JavaScript 安全整数的雪花 ID 在 JSON 输出中始终以字符串形式
 * 传输，防止前端精度丢失。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class AdminIdentifierSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 用户摘要与角色中的雪花 ID 应序列化为字符串。
     *
     * <p>覆盖 {@link AdminRoleResponse} 与 {@link AdminUserSummaryResponse} 的
     * ID 字段，确认输出为带引号的字符串而不是数值字面量。</p>
     *
     * @throws Exception 当 JSON 序列化失败时
     */
    @Test
    void serializesSnowflakeIdentifiersAsStringsForJavascriptClients() throws Exception {
        long snowflakeId = 1989234567890123456L;
        AdminRoleResponse role = new AdminRoleResponse(snowflakeId, "审核员", "PHOTO_REVIEWER");
        AdminUserSummaryResponse user = new AdminUserSummaryResponse(
                snowflakeId, "用户", null, 0, "15300000000", 1, null, null, null, null, List.of(role));

        String json = objectMapper.writeValueAsString(user);

        assertThat(json).contains("\"id\":\"1989234567890123456\"").doesNotContain("\"id\":1989234567890123456");
    }

    /**
     * 后台审核原因视图的 ID 应序列化为字符串。
     *
     * <p>覆盖 {@link AdminReviewReasonResponse#from} 对领域对象 ID 的字符串化
     * 转换。</p>
     *
     * @throws Exception 当 JSON 序列化失败时
     */
    @Test
    void serializesReviewReasonIdentifierWithoutJavascriptPrecisionLoss() throws Exception {
        long snowflakeId = 1989234567890123456L;
        ReviewReason source = new ReviewReason(snowflakeId, "PHOTO", "OTHER", "其他", null, true, true, 1, 0);

        String json = objectMapper.writeValueAsString(AdminReviewReasonResponse.from(source));

        assertThat(json).contains("\"id\":\"1989234567890123456\"");
    }
}
