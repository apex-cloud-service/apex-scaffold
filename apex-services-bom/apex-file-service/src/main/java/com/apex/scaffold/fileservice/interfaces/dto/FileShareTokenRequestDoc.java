package com.apex.scaffold.fileservice.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 分享兑换/下载的请求文档模型。
 *
 * <p>令牌通过请求体提交而不放在路径或查询串中，避免被网关访问日志记录。</p>
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Schema(name = "FileShareTokenRequest", description = "分享令牌兑换请求")
@Data
public class FileShareTokenRequestDoc {

    /** 明文分享令牌。 */
    @NotBlank(message = "分享令牌不能为空")
    @Schema(description = "创建分享时返回的明文令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    private String token;
}
