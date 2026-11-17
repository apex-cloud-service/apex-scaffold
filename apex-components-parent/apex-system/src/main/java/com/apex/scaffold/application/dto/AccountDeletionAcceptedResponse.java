package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号注销异步清理受理结果响应
 *
 * <p>当用户申请注销账号时，系统立即失效账号并返回此响应对象。
 * 受理后清理任务在后台异步执行，客户端应保存 {@code requestId} 用于轮询清理进度。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>用户申请注销账号：调用注销 API 后立即返回</li>
 *   <li>轮询清理进度：客户端使用 {@code requestId} 查询清理任务状态</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code requestId}：本次注销请求的唯一标识，用于轮询清理进度</li>
 *   <li>{@code jobId}：后台异步清理任务的 ID</li>
 *   <li>{@code status}：受理状态，固定为 "ACCEPTED"</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * AccountDeletionAcceptedResponse response = AccountDeletionAcceptedResponse.builder()
 *     .requestId("account-deletion-01JAZ8Y7N2M4P6Q8R0STUVWX12")
 *     .jobId("1876543210123456789")
 *     .status("ACCEPTED")
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AccountDeletionAcceptedResponse", description = "账号注销异步清理受理结果")
public class AccountDeletionAcceptedResponse {

    /**
     * 本次注销请求标识
     *
     * <p>客户端应保存此标识用于轮询清理进度。
     * 标识格式：{@code account-deletion-} + ULID（26 位字符）。
     *
     * <p>示例：{@code account-deletion-01JAZ8Y7N2M4P6Q8R0STUVWX12}
     */
    @Schema(description = "本次注销请求标识", example = "account-deletion-01JAZ8Y7N2M4P6Q8R0STUVWX12")
    private String requestId;

    /**
     * 异步清理任务 ID
     *
     * <p>后台异步清理任务的唯一标识，用于内部任务跟踪和调度。
     * 此 ID 由任务调度系统生成。
     *
     * <p>示例：{@code 1876543210123456789}
     */
    @Schema(description = "异步清理任务 ID", example = "1876543210123456789")
    private String jobId;

    /**
     * 受理状态
     *
     * <p>账号注销请求受理状态，固定返回 "ACCEPTED" 表示已受理。
     * 实际清理任务的执行状态需要通过轮询 API 查询。
     *
     * <p>可选值：
     * <ul>
     *   <li>{@code ACCEPTED}：已受理，清理任务已提交到后台队列</li>
     * </ul>
     */
    @Schema(
            description = "受理状态",
            example = "ACCEPTED",
            allowableValues = {"ACCEPTED"})
    private String status;
}
