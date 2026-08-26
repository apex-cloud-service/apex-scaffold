package com.apex.scaffold.domain.gateway;

import com.apex.scaffold.domain.model.moderation.TextModerationResult;
import reactor.core.publisher.Mono;

/**
 * 文本内容安全审核端口。
 *
 * <p>定义文本内容审核的领域端口，将外部内容审核服务（如百度智能云内容审核）
 * 的 HTTP 调用细节隔离在基础设施层。应用层仅依赖此端口，不感知具体的审核
 * API 路径、鉴权方式和响应格式。</p>
 *
 * <h2>设计意图</h2>
 * <ul>
 *   <li>兴趣同频等 UGC 场景在消息/话题/开场白落库前先审后发</li>
 *   <li>审核结果使用领域类型 {@link TextModerationResult} 传递，不退化为基础类型</li>
 *   <li>与 {@link ImageModerationPort} 保持一致：远程失败由调用方按失败关闭处理</li>
 * </ul>
 *
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
public interface TextModerationPort {

    /**
     * 审核一段文本。
     *
     * @param text 待审核文本；必须非空，长度由适配器按供应商限制校验
     * @return 结构化审核结果
     */
    Mono<TextModerationResult> moderate(String text);
}
