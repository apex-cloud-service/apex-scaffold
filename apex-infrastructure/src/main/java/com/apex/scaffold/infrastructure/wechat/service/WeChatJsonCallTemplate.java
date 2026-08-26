package com.apex.scaffold.infrastructure.wechat.service;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 微信 JSON 调用的统一响应处理模板。
 *
 * <p>各微信子系统仍负责自身 URI、请求体和 token 规则，本模板统一执行动态 JSON 到
 * {@link WeChatApiResponse} 的适配和业务错误码校验。使用 Reactor {@code transform}
 * 可把该模板以 Lambda 友好的方式组合进现有非阻塞调用链，不改变订阅时机。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
public final class WeChatJsonCallTemplate {

    /** 禁止实例化无状态模板工具。 */
    private WeChatJsonCallTemplate() {}

    /**
     * 校验调用结果并保留兼容 Map 响应。
     *
     * @param response WebClient JSON 响应流
     * @param operation 操作名称，用于错误消息
     * @return 已通过微信业务错误码校验的响应流
     */
    public static Mono<Map<String, Object>> requireSuccess(Mono<Map<String, Object>> response, String operation) {
        return response.map(payload -> WeChatApiResponse.from(payload).requireSuccess(operation));
    }
}
