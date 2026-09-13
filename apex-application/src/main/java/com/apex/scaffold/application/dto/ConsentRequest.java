package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户协议同意请求 DTO。
 *
 * <p>用于提交用户对用户协议和隐私政策的同意状态，包含协议版本号、隐私政策版本号、同意标识等。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**n * ConsentRequestn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class ConsentRequest {

    /**
     * 用户协议版本号。
     *
     * <p>用户同意时所阅读的协议版本，不能为空。</p>
     */
    @Schema(description = "协议版本号")
    @NotBlank(message = "{param.not_blank}")
    @Size(max = 64, message = "{param.size_invalid}")
    private String userAgreementVersion;

    /**
     * 隐私政策版本号。
     *
     * <p>用户同意时所阅读的隐私政策版本，不能为空。</p>
     */
    @Schema(description = "隐私政策版本号")
    @NotBlank(message = "{param.not_blank}")
    @Size(max = 64, message = "{param.size_invalid}")
    private String privacyPolicyVersion;

    /**
     * 是否同意。
     *
     * <p>{@code true} 表示用户已同意协议，{@code false} 表示未同意。</p>
     */
    @Schema(description = "是否同意")
    @NotNull(message = "{param.not_null}")
    private Boolean agreed;

    /**
     * 渠道标识（可选）。
     *
     * <p>记录用户首次进入的渠道来源。</p>
     */
    @Schema(description = "渠道标识")
    @Size(max = 32, message = "{param.size_invalid}")
    private String channel;

    /**
     * 设备标识（可选）。
     *
     * <p>客户端生成的唯一设备标识。</p>
     */
    @Schema(description = "设备标识")
    @Size(max = 128, message = "{param.size_invalid}")
    private String deviceId;
}
