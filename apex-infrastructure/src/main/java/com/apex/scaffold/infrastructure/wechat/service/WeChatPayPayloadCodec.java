package com.apex.scaffold.infrastructure.wechat.service;

import com.apex.scaffold.common.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;

/**
 * 微信支付请求与回调 JSON 负载编解码器。
 *
 * <p>统一复用经过配置的 Jackson 映射器，避免支付服务维护手写 JSON 转义逻辑。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
public final class WeChatPayPayloadCodec {

    /** Map 类型引用。 */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /**
     * 序列化支付请求负载。
     *
     * @param payload 请求负载
     * @return JSON
     */
    public String encode(Map<String, Object> payload) {
        return JsonUtil.toJson(payload);
    }

    /**
     * 解析支付回调负载。
     *
     * @param json JSON
     * @return 回调字段
     */
    public Map<String, Object> decode(String json) {
        try {
            return JsonUtil.getObjectMapper().readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("微信支付回调JSON格式错误", exception);
        }
    }
}
