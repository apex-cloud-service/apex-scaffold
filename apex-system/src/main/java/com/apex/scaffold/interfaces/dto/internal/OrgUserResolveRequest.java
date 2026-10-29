package com.apex.scaffold.interfaces.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 组织目录内部接口：批量解析真实系统用户请求。
 *
 * <p>调用方（如 office 组织同步）持有外部通讯录的用户标识，需要映射到本租户
 * {@code sys_user.id}。每个条目至少提供一个可用于匹配的字段（手机号、邮箱或登录名），
 * 三者都为空时该条目不参与匹配。</p>
 *
 * @param users 待解析用户列表，必须非空
 * @author Apex Team
 * @since 2.6.0
 */
@Schema(description = "组织目录批量解析系统用户请求")
public record OrgUserResolveRequest(
        @Schema(description = "待解析用户列表，单次最多 200 条", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotEmpty(message = "待解析用户列表不能为空")
                @Size(max = 200, message = "单次最多解析 200 个用户")
                List<@Valid OrgUserRef> users) {

    /**
     * 单个外部用户引用。
     *
     * @param externalUserId 外部系统用户标识，原样回传，便于调用方对账
     * @param mobile         手机号，可为空
     * @param email          邮箱，可为空
     * @param username       登录名，可为空
     * @param userId         已绑定的系统用户 ID；提供时以主键精确匹配，优先于其他字段
     */
    @Schema(description = "外部用户引用")
    public record OrgUserRef(
            @Schema(description = "外部系统用户标识，原样回传", requiredMode = Schema.RequiredMode.REQUIRED) String externalUserId,
            @Schema(description = "手机号") String mobile,
            @Schema(description = "邮箱") String email,
            @Schema(description = "登录名") String username,
            @Schema(description = "已绑定的系统用户 ID，提供时以主键精确匹配") Long userId) {}
}
