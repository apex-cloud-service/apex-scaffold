package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息DTO
 *
 * <p>用于接口层返回用户的公开信息，避免领域实体直接暴露，包含用户基本信息、状态、登录时间等
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    /** 用户 ID，系统内部用户唯一标识。 */
    @Schema(description = "用户ID，系统内部用户唯一标识")
    private Long id;

    /** 微信 OpenID，用户在微信开放平台的唯一标识。 */
    @Schema(description = "微信OpenID，用户在微信开放平台的唯一标识")
    private String openId;

    /** 微信 UnionID，同一主体下的唯一标识，需要绑定微信开放平台账号。 */
    @Schema(description = "微信UnionID，同一主体下的唯一标识，需要绑定微信开放平台账号")
    private String unionId;

    /** 昵称，用户的微信昵称。 */
    @Schema(description = "昵称，用户的微信昵称")
    private String nickName;

    /** 头像 URL，用户的微信头像地址。 */
    @Schema(description = "头像URL，用户的微信头像地址")
    private String avatarUrl;

    /** 性别（0-未知，1-男，2-女）。 */
    @Schema(description = "性别（0-未知，1-男，2-女）")
    private Integer gender;

    /** 生日。 */
    @Schema(description = "生日，格式为YYYY-MM-DD")
    private LocalDate birthday;

    /** 手机号，用户的手机号码，需用户授权后获取。 */
    @Schema(description = "手机号，用户的手机号码，需用户授权后获取")
    private String phone;

    /** 状态（0-禁用，1-正常）。 */
    @Schema(description = "状态（0-禁用，1-正常）")
    private Integer status;

    /** 最后登录时间。 */
    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    /** 创建时间，用户账号创建的时间。 */
    @Schema(description = "创建时间，用户账号创建的时间")
    private LocalDateTime createTime;
}
