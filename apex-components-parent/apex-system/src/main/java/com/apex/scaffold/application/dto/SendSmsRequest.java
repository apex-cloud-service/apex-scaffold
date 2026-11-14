package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送短信验证码请求
 *
 * <p>用于触发向指定手机号发送短信验证码。验证码用于登录、注册、注销等敏感操作的二次确认。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>短信验证码登录：用户使用手机号 + 验证码登录</li>
 *   <li>账号注销验证：注销账号前的短信二次确认</li>
 *   <li>手机号绑定/更换：绑定或更换手机号时验证新手机号</li>
 *   <li>敏感操作验证：修改密码、支付等敏感操作</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>格式：6 位数字</li>
 *   <li>有效期：5 分钟</li>
 *   <li>一次性：验证后失效</li>
 *   <li>频率限制：同一手机号 1 分钟内最多发送 1 次</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * SendSmsRequest request = SendSmsRequest.builder()
 *     .phone("13800138000")
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see com.apex.scaffold.application.service.AuthService#sendSms
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SendSmsRequest", description = "发送短信验证码请求")
public class SendSmsRequest {

    /**
     * 目标手机号
     *
     * <p>接收短信验证码的手机号，必须符合中国大陆手机号格式（11 位，1 开头）。
     *
     * <p>验证规则：
     * <ul>
     *   <li>必填</li>
     *   <li>必须为 11 位数字</li>
     *   <li>第一位必须是 1</li>
     *   <li>第二位必须是 3-9</li>
     * </ul>
     *
     * <p>频率限制：
     * <ul>
     *   <li>同一手机号 1 分钟内最多发送 1 次</li>
     *   <li>同一 IP 1 分钟内最多发送 5 次</li>
     * </ul>
     *
     * <p>示例：{@code 13800138000}
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "目标手机号（中国大陆 11 位手机号）", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;
}
