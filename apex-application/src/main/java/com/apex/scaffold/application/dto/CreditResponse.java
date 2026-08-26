package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 算力响应DTO（CreditResponse）
 *
 * <p>用于返回用户算力信息，包含算力余额、上限及今日登录奖励领取状态等字段。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditResponse {

    /**
     * 算力余额
     *
     * <p>用户当前剩余的算力值
     */
    @Schema(description = "算力余额")
    private Long balance;

    /**
     * 算力上限
     *
     * <p>算力的最大上限值，固定为2000
     */
    @Schema(description = "算力上限")
    @Builder.Default
    private Long limit = 2000L;

    /**
     * 今日登录奖励已领取
     *
     * <p>标识用户今日是否已领取登录奖励
     */
    @Schema(description = "今日登录奖励已领取")
    private Boolean todayLoginBonusReceived;
}
