package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户算力信息响应
 *
 * <p>返回用户的算力（Credit）相关信息，包括当前余额、上限以及今日登录奖励领取状态。
 * 算力是系统内的虚拟资源，用于限制用户使用某些功能的频率或次数。
 *
 * <h2>算力机制</h2>
 * <ul>
 *   <li>算力余额：用户当前可用的算力点数</li>
 *   <li>算力上限：固定为 2000，算力不能超过此值</li>
 *   <li>算力消耗：使用特定功能时扣除相应算力</li>
 *   <li>算力恢复：每日自动恢复，或通过任务获取</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>用户登录后获取算力信息</li>
 *   <li>使用功能前检查算力是否充足</li>
 *   <li>展示今日登录奖励领取状态</li>
 *   <li>算力变化后刷新显示</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * CreditResponse response = CreditResponse.builder()
 *     .balance(1500L)
 *     .limit(2000L)
 *     .todayLoginBonusReceived(true)
 *     .build();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see com.apex.scaffold.application.service.CreditService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreditResponse", description = "用户算力信息响应")
/**n * CreditResponsen *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class CreditResponse {

    /**
     * 算力余额
     *
     * <p>用户当前剩余的算力点数。算力用于限制用户使用特定功能的频率。
     * 余额范围：0 ~ 2000（上限）。
     *
     * <p>消耗规则（示例）：
     * <ul>
     *   <li>生成内容：消耗 10 点</li>
     *   <li>上传文件：消耗 5 点</li>
     *   <li>高级功能：消耗 50 点</li>
     * </ul>
     *
     * <p>恢复机制：
     * <ul>
     *   <li>每日自动恢复到上限</li>
     *   <li>完成任务可获得额外算力</li>
     *   <li>登录奖励可获得算力</li>
     * </ul>
     */
    @Schema(description = "算力余额（当前可用点数）", example = "1500")
    private Long balance;

    /**
     * 算力上限
     *
     * <p>算力的最大上限值，当前固定为 2000。
     * 算力余额不能超过此值，超出部分会被丢弃。
     *
     * <p>默认值：{@code 2000L}
     */
    @Schema(description = "算力上限（最大值）", example = "2000")
    @Builder.Default
    private Long limit = 2000L;

    /**
     * 今日登录奖励已领取
     *
     * <p>标识用户今日是否已领取登录奖励。每日零点重置为 {@code false}。
     *
     * <p>领取规则：
     * <ul>
     *   <li>每日首次登录可领取</li>
     *   <li>领取后获得固定算力（如 100 点）</li>
     *   <li>每日零点重置领取状态</li>
     * </ul>
     *
     * <p>可能值：
     * <ul>
     *   <li>{@code true}：今日已领取</li>
     *   <li>{@code false}：今日未领取，可以领取</li>
     * </ul>
     */
    @Schema(description = "今日登录奖励已领取", example = "true")
    private Boolean todayLoginBonusReceived;
}
