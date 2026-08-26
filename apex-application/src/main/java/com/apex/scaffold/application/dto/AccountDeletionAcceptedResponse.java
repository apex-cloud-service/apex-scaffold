package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号已失效、异步清理任务已受理的响应数据。
 *
 * <p>受理后清理任务在后台异步执行，客户端应保存请求标识用于轮询清理进度。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AccountDeletionAcceptedResponse", description = "账号注销异步清理受理结果")
public class AccountDeletionAcceptedResponse {

    @Schema(description = "本次注销请求标识", example = "account-deletion-01JAZ8Y7N2M4P6Q8R0STUVWX12")
    private String requestId;

    @Schema(description = "异步清理任务 ID", example = "1876543210123456789")
    private String jobId;

    @Schema(
            description = "受理状态",
            example = "ACCEPTED",
            allowableValues = {"ACCEPTED"})
    private String status;
}
