package com.apex.scaffold.infrastructure.mq.constant;

/**
 * 消息队列常量类
 *
 * <p>定义 RocketMQ 的主题、标签等常量
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class MqConstant {

    /** 私有构造函数，防止实例化 */
    private MqConstant() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }

    // ==================== 主题定义 ====================

    /** 订单主题 */
    public static final String TOPIC_ORDER = "order-topic";

    /** 用户主题 */
    public static final String TOPIC_USER = "user-topic";

    /** 支付主题 */
    public static final String TOPIC_PAY = "pay-topic";

    /** 通知主题 */
    public static final String TOPIC_NOTIFY = "notify-topic";

    /** 日志主题 */
    public static final String TOPIC_LOG = "log-topic";

    /** 照片人工审核请求主题。 */
    public static final String TOPIC_PHOTO_MODERATION = "apex-photo-moderation";

    /** 站内通知（SSE 广播）主题。 */
    public static final String TOPIC_NOTIFICATION = "apex-notification-topic";

    /** 站内通知（SSE 广播）消费组。 */
    public static final String CONSUMER_GROUP_NOTIFICATION = "apex-notification-consumer-group";

    /** 照片人工审核结果主题。 */
    public static final String TOPIC_PHOTO_MODERATION_RESULT = "apex-photo-moderation-result";

    /** User photo reports destined for the administration review system. */
    public static final String TOPIC_PHOTO_REPORT = "apex-photo-report";

    /** A report submitted for a photo shown in a vote. */
    public static final String TAG_PHOTO_REPORTED = "photo-reported-v1";

    /** 人工审核请求标签。 */
    public static final String TAG_MANUAL_REVIEW_REQUESTED = "manual-review-requested-v1";

    /** 人工审核结果标签。 */
    public static final String TAG_MANUAL_REVIEW_DECIDED = "manual-review-decided-v1";

    // ==================== 订单标签 ====================

    /** 订单创建标签 */
    public static final String TAG_ORDER_CREATE = "create";

    /** 订单支付标签 */
    public static final String TAG_ORDER_PAY = "pay";

    /** 订单取消标签 */
    public static final String TAG_ORDER_CANCEL = "cancel";

    /** 订单完成标签 */
    public static final String TAG_ORDER_COMPLETE = "complete";

    // ==================== 用户标签 ====================

    /** 用户注册标签 */
    public static final String TAG_USER_REGISTER = "register";

    /** 用户登录标签 */
    public static final String TAG_USER_LOGIN = "login";

    /** 用户退出标签 */
    public static final String TAG_USER_LOGOUT = "logout";

    /** 用户信息更新标签 */
    public static final String TAG_USER_UPDATE = "update";

    // ==================== 支付标签 ====================

    /** 支付成功标签 */
    public static final String TAG_PAY_SUCCESS = "success";

    /** 支付失败标签 */
    public static final String TAG_PAY_FAIL = "fail";

    /** 支付退款标签 */
    public static final String TAG_PAY_REFUND = "refund";

    // ==================== 通知标签 ====================

    /** 短信通知标签 */
    public static final String TAG_NOTIFY_SMS = "sms";

    /** 邮件通知标签 */
    public static final String TAG_NOTIFY_EMAIL = "email";

    /** 推送通知标签 */
    public static final String TAG_NOTIFY_PUSH = "push";

    // ==================== 日志标签 ====================

    /** 操作日志标签 */
    public static final String TAG_LOG_OPERATION = "operation";

    /** 错误日志标签 */
    public static final String TAG_LOG_ERROR = "error";

    /** 业务日志标签 */
    public static final String TAG_LOG_BUSINESS = "business";

    // ==================== 消费组 ====================

    /** 订单消费者组 */
    public static final String CONSUMER_GROUP_ORDER = "order-consumer-group";

    /** 用户消费者组 */
    public static final String CONSUMER_GROUP_USER = "user-consumer-group";

    /** 支付消费者组 */
    public static final String CONSUMER_GROUP_PAY = "pay-consumer-group";

    /** 通知消费者组 */
    public static final String CONSUMER_GROUP_NOTIFY = "notify-consumer-group";

    /** 日志消费者组 */
    public static final String CONSUMER_GROUP_LOG = "log-consumer-group";

    // ==================== 其他常量 ====================

    /** 消息重试次数 */
    public static final int MESSAGE_RETRY_TIMES = 3;

    /** 消息超时时间（毫秒） */
    public static final int MESSAGE_TIMEOUT = 30000;

    /** 消息延迟等级（1s, 5s, 10s, 30s, 1m, 2m, 3m, 4m, 5m, 6m, 7m, 8m, 9m, 10m, 20m, 30m, 1h, 2h） */
    public static final int DELAY_LEVEL_1S = 1;

    /** 延迟 5 秒 */
    public static final int DELAY_LEVEL_5S = 2;

    /** 延迟 10 秒 */
    public static final int DELAY_LEVEL_10S = 3;

    /** 延迟 30 秒 */
    public static final int DELAY_LEVEL_30S = 4;

    /** 延迟 1 分钟 */
    public static final int DELAY_LEVEL_1M = 5;

    /** 延迟 2 分钟 */
    public static final int DELAY_LEVEL_2M = 6;

    /** 延迟 3 分钟 */
    public static final int DELAY_LEVEL_3M = 7;

    /** 延迟 4 分钟 */
    public static final int DELAY_LEVEL_4M = 8;

    /** 延迟 5 分钟 */
    public static final int DELAY_LEVEL_5M = 9;

    /** 延迟 6 分钟 */
    public static final int DELAY_LEVEL_6M = 10;

    /** 延迟 7 分钟 */
    public static final int DELAY_LEVEL_7M = 11;

    /** 延迟 8 分钟 */
    public static final int DELAY_LEVEL_8M = 12;

    /** 延迟 9 分钟 */
    public static final int DELAY_LEVEL_9M = 13;

    /** 延迟 10 分钟 */
    public static final int DELAY_LEVEL_10M = 14;

    /** 延迟 20 分钟 */
    public static final int DELAY_LEVEL_20M = 15;

    /** 延迟 30 分钟 */
    public static final int DELAY_LEVEL_30M = 16;

    /** 延迟 1 小时 */
    public static final int DELAY_LEVEL_1H = 17;

    /** 延迟 2 小时 */
    public static final int DELAY_LEVEL_2H = 18;

    // ==================== 死信队列 ====================

    /** 重试消费最大次数（超过此次数将投递到死信队列） */
    public static final int MAX_RECONSUME_TIMES = 3;

    /** 死信队列主题后缀 */
    public static final String DLQ_TOPIC_SUFFIX = "-dlq";
}
