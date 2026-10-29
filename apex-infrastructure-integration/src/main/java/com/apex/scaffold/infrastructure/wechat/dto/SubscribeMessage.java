package com.apex.scaffold.infrastructure.wechat.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信订阅消息DTO
 *
 * <p>微信订阅消息是微信提供的长期消息推送能力，替代了原有的模板消息（已逐步下线）。
 * 用户需要主动订阅后，服务端才可向用户发送订阅消息。
 *
 * <h3>与模板消息的区别</h3>
 * <ul>
 *   <li><b>模板消息</b>：已逐步下线，不再支持新接入</li>
 *   <li><b>订阅消息</b>：需要用户主动订阅，分为一次性订阅和长期订阅</li>
 *   <li><b>一次性订阅</b>：用户订阅一次，可推送一条消息</li>
 *   <li><b>长期订阅</b>：用户订阅一次，可长期推送（仅限特定行业）</li>
 * </ul>
 *
 * <h3>使用流程</h3>
 * <ol>
 *   <li>在微信公众平台申请订阅消息模板</li>
 *   <li>前端调用wx.requestSubscribeMessage引导用户订阅</li>
 *   <li>服务端通过本DTO封装消息数据并调用发送接口</li>
 * </ol>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code toUser} - 接收者OpenID，必填</li>
 *   <li>{@code templateId} - 订阅消息模板ID，必填</li>
 *   <li>{@code page} - 点击消息跳转的小程序页面路径，可选</li>
 *   <li>{@code data} - 模板内容，key为模板中的字段名</li>
 *   <li>{@code miniprogramState} - 跳转小程序类型：developer(开发版)、trial(体验版)、formal(正式版)</li>
 *   <li>{@code lang} - 语言：zh_CN(简体中文)、en_US(英文)</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeMessage implements Serializable {

    /**
     * 序列化版本UID
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 接收者（用户）的OpenID
     */
    private String toUser;

    /**
     * 所需下发的订阅消息模板ID
     *
     * <p>在微信公众平台 - 功能 - 订阅消息中申请
     */
    private String templateId;

    /**
     * 点击消息跳转的小程序页面路径
     *
     * <p>如 {@code pages/index/index}，不填则无跳转
     */
    private String page;

    /**
     * 模板内容
     *
     * <p>key对应模板中的字段名（如 thing1、time2、thing3等），
     * value为{@link SubscribeData}对象
     */
    @Builder.Default
    private Map<String, SubscribeData> data = new java.util.HashMap<>(4);

    /**
     * 跳转小程序类型
     *
     * <p>可选值：
     * <ul>
     *   <li>{@code developer} - 开发版</li>
     *   <li>{@code trial} - 体验版</li>
     *   <li>{@code formal} - 正式版（默认）</li>
     * </ul>
     */
    private String miniprogramState;

    /**
     * 语言
     *
     * <p>可选值：
     * <ul>
     *   <li>{@code zh_CN} - 简体中文（默认）</li>
     *   <li>{@code en_US} - 英文</li>
     * </ul>
     */
    private String lang;

    /**
     * 订阅消息数据项
     *
     * <p>每个模板字段对应的具体内容，可设置值和颜色。
     *
     * @author Apex Team
     * @since 1.0.0
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeData implements Serializable {

        /**
         * 序列化版本UID
         */
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 模板内容值
         *
         * <p>例如："恭喜你成功报名"、 "2025年1月1日"等
         */
        private String value;

        /**
         * 模板内容颜色
         *
         * <p>如 {@code #173177}，不填则默认黑色
         */
        private String color;
    }
}
