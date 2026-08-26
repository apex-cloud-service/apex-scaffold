package com.apex.scaffold.infrastructure.wechat.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板消息DTO
 *
 * <p>微信模板消息发送参数
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateMessage implements Serializable {

    /** 序列化版本 UID */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 接收者 OpenID */
    private String toUser;

    /** 模板ID */
    private String templateId;

    /** 模板跳转页面 */
    private String page;

    /** 模板数据 */
    @Builder.Default
    private Map<String, TemplateData> data = new java.util.HashMap<>(4);

    /** 模板数据项 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateData implements Serializable {

        /** 序列化版本 UID */
        @Serial
        private static final long serialVersionUID = 1L;

        /** 数据值 */
        private String value;

        /** 颜色 */
        private String color;
    }
}
