package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号注销短信二次验证请求
 *
 * <p>用户申请注销账号时，系统要求进行短信验证码二次确认。
 * 客户端只需提供验证码，手机号从当前登录用户的绑定手机号自动获取。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>用户在 APP 中申请注销账号</li>
 *   <li>系统发送短信验证码到用户绑定的手机号</li>
 *   <li>用户输入验证码提交此请求</li>
 *   <li>验证通过后系统失效账号并启动清理任务</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>手机号隐式获取：防止恶意指定他人手机号</li>
 *   <li>验证码有效期：通常 5 分钟</li>
 *   <li>频率限制：同一用户 1 分钟内最多申请 3 次</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * AccountDeletionRequest request = AccountDeletionRequest.builder()
 *     .code("123456")
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see com.apex.scaffold.application.service.UserService#deleteAccount
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AccountDeletionRequest", description = "账号注销短信二次验证请求")
public class AccountDeletionRequest {

    /**
     * 短信验证码
     *
     * <p>发送到当前登录用户绑定手机号的 6 位数字验证码。
     * 手机号始终从当前登录账号读取，客户端不能通过请求体指定手机号，
     * 这样可以防止恶意用户注销他人账号。
     *
     * <p>验证规则：
     * <ul>
     *   <li>必填</li>
     *   <li>必须为 6 位数字</li>
     *   <li>有效期通常 5 分钟</li>
     *   <li>一次性使用，验证后失效</li>
     * </ul>
     *
     * <p>示例：{@code 123456}
     */
    @NotBlank(message = "注销验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "注销验证码必须为6位数字")
    @Schema(description = "发送到当前账号绑定手机号的 6 位短信验证码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
