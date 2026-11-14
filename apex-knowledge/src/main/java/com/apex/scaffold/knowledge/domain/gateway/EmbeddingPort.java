package com.apex.scaffold.knowledge.domain.gateway;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 文本向量化端口（OpenAI 兼容协议的领域抽象）。
 *
 * <p>实现方负责批量调用、超时与错误转换；输入顺序与输出顺序一一对应。
 * 未配置供应商时由降级装配提供实现并向编排层发出明确错误，而非返回空结果。
 *
 * @author Apex Team
 * @since 1.3.2
 */
public interface EmbeddingPort {

    /**
     * 批量向量化文本。
     *
     * @param texts 非空文本列表（每条非空白）
     * @return 与输入顺序对应的向量列表
     */
    Mono<List<float[]>> embed(List<String> texts);
}
