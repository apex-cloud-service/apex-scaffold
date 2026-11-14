package com.apex.scaffold.application.security;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 图形验证码响应 DTO。
 *
 * <p>承载验证码 ID 与 Base64 编码的 PNG 图片，前端通过
 * {@code captchaId} 在登录时回传校验。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "图形验证码响应")
public class CaptchaVO {

    /**
     * 构造图形验证码响应。
     *
     * @param captchaId 验证码 ID，登录时回传
     * @param image Base64 编码的 PNG 图片（不含 data URI 前缀）
     */
    public CaptchaVO(String captchaId, String image) {
        this.captchaId = captchaId;
        this.image = image;
    }

    /**
     * 验证码 ID。
     *
     * <p>服务端生成的一次性标识，登录时与用户输入一同回传。</p>
     */
    @Schema(description = "验证码ID")
    private String captchaId;

    /**
     * Base64 编码的验证码图片。
     *
     * <p>PNG 格式，不含 {@code data:image/png;base64,} 前缀，
     * 前端拼接后直接展示。</p>
     */
    @Schema(description = "Base64编码的PNG图片")
    private String image;
}
