package com.apex.scaffold.office.domain;

/**
 * 办公平台枚举
 *
 * <p>定义系统支持的企业办公平台类型，用于区分不同平台的认证、API 调用和数据隔离。
 *
 * @author Apex Team
 * @since 1.2.0
 */
public enum OfficePlatform {
    /** 飞书（Lark/Feishu） */
    FEISHU,

    /** 钉钉（DingTalk） */
    DINGTALK,

    /** 企业微信（WeCom） */
    WECOM
}
