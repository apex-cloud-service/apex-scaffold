package com.apex.scaffold.common.governance;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * 可参与统一关停排空的资源契约。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface DrainParticipant {
    /**
     * 返回稳定的参与者名称。
     *
     * @return 参与者名称
     */
    String name();

    /**
     * 停止接收新任务或消息。
     */
    void stopAccepting();

    /**
     * 在统一截止时间前等待已接收任务完成。
     *
     * @param deadline 统一截止时间
     * @return 异步排空结果
     */
    CompletionStage<DrainResult> drain(Instant deadline);

    /**
     * 截止时间到达后的强制停止。
     */
    void forceStop();
}
