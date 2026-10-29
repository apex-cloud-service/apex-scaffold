package com.apex.scaffold.fileservice.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建文件分享的请求文档模型。
 *
 * <p>有效期由服务端收敛到配置区间；省略时使用服务端默认值。</p>
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Schema(name = "FileShareCreateRequest", description = "创建限时只读文件分享")
@Data
public class FileShareCreateRequestDoc {

    /** 期望的分享有效期（秒）。 */
    @Schema(
            description = "分享有效期（秒），省略时使用服务端默认值，服务端会收敛到配置区间",
            example = "86400",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer expireSeconds;
}
