package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完成手机号或微信注册的请求。
 *
 *
 *<p>用户登录后若为首次访问，需提交性别、生日等必填资料完成注册。
 *{@code registrationTicket} 由登录流程签发，用于绑定新注册用户与登录渠道身份。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**n * RegisterRequestn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class RegisterRequest {

    /**
     * 一次性注册票据。
     *
     * <p>由登录或微信绑定流程签发，用于将注册资料与已校验的登录渠道身份关联。</p>
     */
    @Schema(description = "登录或微信绑定流程签发的一次性注册票据")
    @NotBlank(message = "{param.not_blank}")
    private String registrationTicket;

    /**
     * 用户性别。
     *
     * <p>取值：1-男，2-女。</p>
     */
    @Schema(description = "性别：1-男，2-女")
    @NotNull(message = "{param.not_null}")
    @Min(value = 1, message = "{param.range_invalid}")
    @Max(value = 2, message = "{param.range_invalid}")
    private Integer gender;

    /**
     * 生日。
     *
     * <p>格式为 {@code YYYY-MM-DD}，必须为过去的日期。</p>
     */
    @Schema(description = "生日，格式为YYYY-MM-DD")
    @NotNull(message = "{param.not_null}")
    @Past(message = "{param.invalid}")
    private LocalDate birthday;

    /**
     * 昵称（可选）。
     *
     * <p>不填时自动生成 "Apex用户" 加八位随机数字。</p>
     */
    @Schema(description = "昵称；不填时自动生成Apex用户加八位随机数字")
    @Size(max = 50, message = "{param.size_invalid}")
    private String nickName;

    /**
     * 设备标识（可选）。
     *
     * <p>客户端生成的唯一设备标识，用于设备绑定和安全审计。</p>
     */
    @Schema(description = "设备标识")
    @Size(max = 200, message = "{param.size_invalid}")
    private String deviceId;

    /**
     * 客户端 IP 地址（内部自动填充）。
     *
     * <p>由 Handler 从 HTTP 请求中提取，不对外暴露。</p>
     */
    @Schema(hidden = true)
    private String clientIp;
}
