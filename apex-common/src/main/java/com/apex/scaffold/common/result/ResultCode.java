package com.apex.scaffold.common.result;

import java.util.Arrays;
import lombok.Getter;

/**
 * 响应状态码枚举类
 *
 * <p>定义系统中使用的所有响应状态码及其对应的国际化消息键（messageKey）。
 * 消息的具体文本存储在 {@code apex-interfaces/src/main/resources/i18n/messages_*.properties} 中，
 * 通过接口层的 {@code MessageUtils} 根据客户端语言偏好动态解析。
 *
 * <h2>状态码分类</h2>
 * <table border="1">
 *   <caption>错误码编码规则</caption>
 *   <tr><th>范围</th><th>分类</th><th>说明</th></tr>
 *   <tr><td>0</td><td>成功</td><td>请求处理成功</td></tr>
 *   <tr><td>-1</td><td>系统错误</td><td>未分类的通用系统异常</td></tr>
 *   <tr><td>4xx</td><td>客户端错误</td><td>对齐 HTTP 语义</td></tr>
 *   <tr><td>1001~1016</td><td>微信/认证相关</td><td>微信登录/认证/短信/Token异常</td></tr>
 *   <tr><td>2001~2003</td><td>流量治理</td><td>Sentinel 限流/熔断/降级</td></tr>
 *   <tr><td>3001~3006</td><td>基础设施</td><td>缓存/数据库/消息队列异常</td></tr>
 *   <tr><td>4001~4602</td><td>业务错误</td><td>具体业务逻辑/文件校验异常</td></tr>
 *   <tr><td>5001</td><td>安全防护</td><td>防重放攻击校验异常</td></tr>
 * </table>
 *
 * <h2>使用说明</h2>
 * <p>
 * 每个枚举值包含一个 {@code code}（状态码）和一个 {@code messageKey}（消息键）。
 * 消息键对应 properties 文件中的 key，通过 Spring {@link org.springframework.context.MessageSource}
 * 解析为对应语言的文本。同时保留 {@code msg} 字段存储简体中文默认消息，
 * 用于日志记录和向后兼容。
 * </p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "成功", "sys.success"),

    /** 系统错误 */
    ERROR(-1, "系统错误", "sys.error"),

    // ========== 客户端错误 ==========

    /** 参数错误 */
    PARAM_ERROR(400, "参数错误", "sys.param_error"),

    /** 未授权 */
    UNAUTHORIZED(401, "未授权", "sys.unauthorized"),

    /** 登录已过期，需要重新登录 */
    TOKEN_EXPIRED(40101, "登录已过期，请重新登录", "auth.token_expired"),

    /** 禁止访问 */
    FORBIDDEN(403, "禁止访问", "sys.forbidden"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在", "sys.not_found"),

    // ========== 微信相关错误 ==========

    /** 微信登录失败 */
    WX_LOGIN_FAILED(1001, "微信登录失败", "wx.login_failed"),

    /** 微信接口调用失败 */
    WX_API_ERROR(1003, "微信接口调用失败", "wx.api_error"),

    /** 微信授权失败 */
    WX_AUTH_FAILED(1004, "微信授权失败", "wx.auth_failed"),

    /** 微信用户信息获取失败 */
    WX_USER_INFO_ERROR(1005, "微信用户信息获取失败", "wx.user_info_error"),

    /** 不支持的登录类型 */
    UNSUPPORTED_LOGIN_TYPE(1006, "不支持的登录类型", "wx.unsupported_login_type"),

    /** 登录失败 */
    LOGIN_FAILED(1007, "登录失败", "wx.login_failed_generic"),

    // ========== 认证业务错误（1008~1016） ==========

    /** 登录锁定，失败次数过多 */
    LOGIN_LOCKED(1008, "登录失败次数过多，请稍后再试", "auth.login_locked"),

    /** RefreshToken 已失效 */
    REFRESH_TOKEN_INVALID(1009, "RefreshToken已失效", "auth.refresh_token_invalid"),

    /** Token 与用户不匹配 */
    TOKEN_USER_MISMATCH(1010, "Token与用户不匹配", "auth.token_user_mismatch"),

    /** 系统繁忙 */
    SYSTEM_BUSY(1011, "系统繁忙，请稍后再试", "auth.system_busy"),

    /** 获取手机号失败 */
    PHONE_FETCH_FAILED(1012, "获取手机号失败", "auth.phone_fetch_failed"),

    /** 短信发送过于频繁 */
    SMS_RATE_LIMITED(1013, "发送短信过于频繁，请稍后再试", "auth.sms_rate_limited"),

    /** 短信服务异常 */
    SMS_SERVICE_ERROR(1014, "短信服务异常，请稍后再试", "auth.sms_service_error"),

    /** 登录服务异常 */
    LOGIN_SERVICE_ERROR(1015, "登录服务异常，请稍后再试", "auth.login_service_error"),

    /** 刷新Token服务异常 */
    REFRESH_TOKEN_ERROR(1016, "刷新Token服务异常，请稍后再试", "auth.refresh_token_error"),

    /** 一次性认证票据无效或已过期 */
    AUTH_TICKET_INVALID(1017, "认证票据无效或已过期", "auth.ticket_invalid"),

    /** 微信已经绑定其他账号 */
    WECHAT_ALREADY_BOUND(1018, "该微信已绑定其他账号", "auth.wechat_already_bound"),

    /** 手机号已经绑定其他微信 */
    PHONE_ALREADY_BOUND(1019, "该手机号已绑定其他微信", "auth.phone_already_bound"),

    /** 当前账号没有可用于注销二次验证的手机号 */
    PHONE_NOT_BOUND(1020, "当前账号未绑定手机号", "auth.phone_not_bound"),

    /** 注销二次验证短信验证码错误或已过期 */
    SMS_CODE_INVALID(1021, "验证码错误或已过期", "auth.sms_code_invalid"),

    /** 管理员处置后，该身份被禁止再次注册。 */
    REGISTRATION_RESTRICTED(1022, "该身份不允许再次注册", "auth.registration_restricted"),

    // ========== 流量治理错误 ==========

    /** 请求过于频繁 */
    RATE_LIMIT(2001, "请求过于频繁", "flow.rate_limit"),

    /** 服务熔断 */
    CIRCUIT_BREAKER(2002, "服务熔断", "flow.circuit_breaker"),

    /** 服务降级 */
    DEGRADE(2003, "服务降级", "flow.degrade"),

    /** 降级读接口返回缓存兜底数据 */
    DEGRADE_FROM_CACHE(2004, "服务降级，返回缓存数据", "flow.degrade_from_cache"),

    /** 降级写接口返回排队中状态 */
    DEGRADE_QUEUED(2005, "系统繁忙，请求已排队", "flow.degrade_queued"),

    // ========== 基础设施错误 ==========

    /** 缓存错误 */
    CACHE_ERROR(3001, "缓存错误", "infra.cache_error"),

    /** 数据库错误 */
    DB_ERROR(3002, "数据库错误", "infra.db_error"),

    /** 消息队列错误 */
    MQ_ERROR(3003, "消息队列错误", "infra.mq_error"),

    /** 存储服务错误 */
    STORAGE_ERROR(3004, "存储服务错误", "infra.storage_error"),

    /** 分布式锁错误 */
    LOCK_ERROR(3005, "分布式锁错误", "infra.lock_error"),

    /** JSON 处理错误 */
    JSON_ERROR(3006, "JSON 处理错误", "infra.json_error"),

    // ========== 业务错误 ==========

    /** 用户不存在 */
    USER_NOT_FOUND(4001, "用户不存在", "biz.user.not_found"),

    /** 用户已存在 */
    USER_ALREADY_EXISTS(4002, "用户已存在", "biz.user.already_exists"),

    /** 同频未解锁 */
    SAME_FREQUENCY_LOCKED(4101, "同频未解锁", "biz.same_frequency_locked"),

    /** 资料未补完 */
    PROFILE_INCOMPLETE(4102, "资料未补完", "biz.profile_incomplete"),

    /** 青少年模式限制 */
    TEEN_MODE_RESTRICTED(4103, "青少年模式限制", "biz.teen_mode_restricted"),

    /** 跳过次数已达上限 */
    SKIP_LIMIT_REACHED(4201, "跳过次数已达上限", "biz.skip_limit_reached"),

    /** 对局已结算/不可重复提交 */
    BATTLE_ALREADY_SUBMITTED(4202, "对局已结算/不可重复提交", "biz.battle_already_submitted"),

    /** 黄金眼反作弊已触发，需先完成黄金眼测试对局 */
    GOLDEN_EYE_TRIGGERED(4203, "触发黄金眼检测，请先完成测试对局", "biz.golden_eye_triggered"),

    /** 内容不可见 */
    CONTENT_NOT_VISIBLE(4301, "内容不可见", "biz.content_not_visible"),

    /** 标签审核中/未通过 */
    TAG_AUDIT_REQUIRED(4401, "标签审核中/未通过", "biz.tag_audit_required"),

    /** 兴趣图谱功能尚未通过灰度开关开放 */
    INTEREST_FEATURE_DISABLED(4402, "兴趣功能尚未开放", "biz.interest_feature_disabled"),

    /** 输入无法归一到可用的兴趣节点 */
    INTEREST_NODE_NOT_FOUND(4403, "未找到可用的兴趣节点", "biz.interest_node_not_found"),

    /** 输入命中多个兴趣语义，必须由用户确认 */
    INTEREST_AMBIGUOUS(4404, "兴趣含义不明确，请先确认", "biz.interest_ambiguous"),

    /** 兴趣节点已停用、合并或不属于当前激活图版本 */
    INTEREST_NODE_UNAVAILABLE(4405, "兴趣节点当前不可用", "biz.interest_node_unavailable"),

    /** 用户兴趣画像发生并发更新冲突 */
    INTEREST_PROFILE_CONFLICT(4406, "兴趣画像已更新，请重试", "biz.interest_profile_conflict"),

    /** 兴趣星球不存在 */
    INTEREST_CHANNEL_NOT_FOUND(4410, "兴趣星球不存在", "biz.interest_channel_not_found"),

    /** 兴趣星球暂停或关闭 */
    INTEREST_CHANNEL_UNAVAILABLE(4411, "兴趣星球当前不可用", "biz.interest_channel_unavailable"),

    /** 话题不可回应 */
    INTEREST_TOPIC_UNAVAILABLE(4412, "话题当前不可回应", "biz.interest_topic_unavailable"),

    /** 已有活跃即时匹配意图 */
    INTEREST_INTENT_ALREADY_ACTIVE(4413, "已有进行中的即时匹配", "biz.interest_intent_already_active"),

    /** 24 小时信号已过期 */
    INTEREST_SIGNAL_EXPIRED(4414, "信号已过期", "biz.interest_signal_expired"),

    /** 信号已不可用（中性态，避免“被抢走”语义） */
    INTEREST_SIGNAL_UNAVAILABLE(4415, "信号已不可用", "biz.interest_signal_unavailable"),

    /** 匿名兴趣会话不存在 */
    ANONYMOUS_SESSION_NOT_FOUND(4416, "匿名会话不存在", "biz.anonymous_session_not_found"),

    /** 当前用户不是匿名会话参与者 */
    ANONYMOUS_SESSION_FORBIDDEN(4417, "无权访问该匿名会话", "biz.anonymous_session_forbidden"),

    /** 匿名会话已结束 */
    ANONYMOUS_SESSION_CLOSED(4418, "匿名会话已结束", "biz.anonymous_session_closed"),

    /** 未满足揭面条件 */
    REVEAL_NOT_ELIGIBLE(4419, "尚未满足揭面条件", "biz.reveal_not_eligible"),

    /** 已有进行中的揭面申请 */
    REVEAL_REQUEST_ALREADY_ACTIVE(4420, "已有进行中的揭面申请", "biz.reveal_request_already_active"),

    /** 揭面申请已过期 */
    REVEAL_REQUEST_EXPIRED(4421, "揭面申请已过期", "biz.reveal_request_expired"),

    /** 揭面决策冲突 */
    REVEAL_DECISION_CONFLICT(4422, "揭面决策冲突", "biz.reveal_decision_conflict"),

    /** 揭面提醒已发送 */
    REVEAL_REMINDER_ALREADY_SENT(4423, "揭面提醒已发送过", "biz.reveal_reminder_already_sent"),

    /** 揭面资料不完整 */
    REVEAL_PROFILE_INCOMPLETE(4424, "揭面资料不完整", "biz.reveal_profile_incomplete"),

    /** 兴趣申请不存在或不属于当前用户 */
    INTEREST_PROPOSAL_NOT_FOUND(4425, "兴趣申请不存在", "biz.interest_proposal_not_found"),

    /** 兴趣同频子模块未开放 */
    INTEREST_FREQUENCY_DISABLED(4426, "兴趣同频功能尚未开放", "biz.interest_frequency_disabled"),

    /** 配对分低于同频最低阈值，不建立匿名会话 */
    INTEREST_PAIR_SCORE_TOO_LOW(4427, "配对分未达匹配阈值", "biz.interest_pair_score_too_low"),

    /** 内容未通过安全审核（先审后发，拒绝落库） */
    CONTENT_MODERATION_REJECTED(4428, "内容未通过安全审核", "biz.content_moderation_rejected"),

    /** 内容疑似违规，已转入人工复核，暂不发送 */
    CONTENT_MODERATION_REVIEW_PENDING(4429, "内容暂时无法发送", "biz.content_moderation_review_pending"),

    /** 发送频控/重复灌水拦截 */
    INTEREST_SEND_LIMITED(4430, "发送过于频繁，请稍后再试", "biz.interest_send_limited"),

    /** 匿名消息超过撤回时限 */
    ANONYMOUS_MESSAGE_RECALL_EXPIRED(4431, "已超过撤回时限", "biz.anonymous_message_recall_expired"),

    /** 账号已被限制使用兴趣功能（风控/封禁打标） */
    INTEREST_RISK_USER_FORBIDDEN(4432, "当前账号已被限制使用兴趣功能", "biz.interest_risk_user_forbidden"),

    /** 举报内容不合法（类型/目标非法） */
    INTEREST_REPORT_INVALID(4433, "举报内容不合法", "biz.interest_report_invalid"),

    /** 会员权益不足 */
    MEMBERSHIP_REQUIRED(4501, "会员权益不足", "biz.membership_required"),

    /** 会员权益尚未过期，暂不能注销账号 */
    ACCOUNT_DELETION_MEMBERSHIP_ACTIVE(4502, "会员权益尚未过期，暂不能注销账号", "biz.account_deletion.membership_active"),

    /** 账号注销已完成或正在处理 */
    ACCOUNT_DELETION_ALREADY_COMPLETED(4503, "账号已注销或正在注销处理中", "biz.account_deletion.already_completed"),

    // ========== 文件校验错误 ==========

    /** 不支持的文件类型 */
    FILE_TYPE_UNSUPPORTED(4601, "不支持的文件类型", "param.file_type_unsupported"),
    /** 文件类型与扩展名不匹配 */
    FILE_TYPE_MISMATCH(4602, "文件类型与扩展名不匹配", "param.file_type_mismatch"),
    /** 上传照片不是正脸 */
    PHOTO_NOT_FRONTAL(4603, "请上传清晰的正脸照片后重试", "param.photo_not_frontal"),

    // ========== 系统管理错误 ==========

    /** 菜单不存在 */
    MENU_NOT_FOUND(4701, "菜单不存在", "biz.menu.not_found"),
    /** 父菜单不存在 */
    MENU_PARENT_NOT_FOUND(4702, "父菜单不存在", "biz.menu.parent_not_found"),
    /** 存在子菜单，无法删除 */
    MENU_HAS_CHILDREN(4703, "存在子菜单，无法删除", "biz.menu.has_children"),
    /** 按钮类型菜单必须配置权限标识 */
    MENU_PERMS_REQUIRED(4704, "按钮类型菜单必须配置权限标识", "biz.menu.perms_required"),

    /** 字典类型不存在 */
    DICT_TYPE_NOT_FOUND(4711, "字典类型不存在", "biz.dict.type_not_found"),
    /** 字典类型编码已存在 */
    DICT_TYPE_EXISTS(4712, "字典类型编码已存在", "biz.dict.type_exists"),
    /** 字典数据不存在 */
    DICT_DATA_NOT_FOUND(4713, "字典数据不存在", "biz.dict.data_not_found"),

    /** 参数配置不存在 */
    CONFIG_NOT_FOUND(4721, "参数配置不存在", "biz.config.not_found"),
    /** 参数键名已存在 */
    CONFIG_KEY_EXISTS(4722, "参数键名已存在", "biz.config.key_exists"),

    /** 定时任务不存在 */
    JOB_NOT_FOUND(4731, "定时任务不存在", "biz.job.not_found"),
    /** 定时任务执行失败 */
    JOB_INVOKE_FAILED(4732, "定时任务执行失败", "biz.job.invoke_failed"),

    /** 验证码错误或已过期 */
    CAPTCHA_INVALID(4741, "验证码错误或已过期", "biz.captcha.invalid"),

    // ========== 安全防护错误 ==========

    /** 防重放校验失败 */
    ANTI_REPLAY_FAILED(5001, "请求已过期或重复提交", "web.anti_replay_failed"),

    /** 暂无符合目标性别的新照片（候选池枯竭或重建失败） */
    NO_TARGET_GENDER_PHOTO(4204, "当前暂无新的目标性别照片，请稍后再试", "biz.no_target_gender_photo"),

    /** 请求对局不是服务端当前允许操作的交付对局 */
    BATTLE_NOT_CURRENT(4205, "对局不是当前可操作对局，请重新同步", "biz.battle_not_current"),

    /** 单用户推荐候选池在安全过滤与硬门禁后为空 */
    NO_SINGLE_RECOMMENDATION(4206, "暂无符合当前条件的新用户", "biz.no_single_recommendation"),

    /** 单用户推荐已经提交过不同的终态反馈 */
    RECOMMENDATION_FEEDBACK_CONFLICT(4207, "该推荐已经处理，不能重复选择", "biz.recommendation_feedback_conflict"),

    /** 当前业务日期内成功钉住次数已达到会员等级配置上限 */
    PIN_DAILY_LIMIT_REACHED(4208, "今日钉住次数已用完", "biz.pin_daily_limit_reached");

    /** 状态码 */
    private final Integer code;

    /** 状态消息（简体中文，用于日志记录和向后兼容） */
    private final String msg;

    /** 国际化消息键，对应 messages_*.properties 中的 key */
    private final String messageKey;

    /**
     * 构造方法
     *
     * @param code       状态码
     * @param msg        状态消息（简体中文默认值，用于日志显示）
     * @param messageKey 国际化消息键，用于通过 MessageSource 获取多语言消息
     */
    ResultCode(Integer code, String msg, String messageKey) {
        this.code = code;
        this.msg = msg;
        this.messageKey = messageKey;
    }

    /**
     * 根据状态码查找对应的枚举值
     *
     * <p>遍历所有枚举值，返回第一个 code 匹配的 ResultCode。
     * 若未找到匹配项，返回 {@link #ERROR} 作为默认值。
     *
     * @param code 状态码
     * @return 匹配的 ResultCode 枚举值，未找到时返回 {@link #ERROR}
     */
    public static ResultCode fromCode(Integer code) {
        if (code == null) {
            return ERROR;
        }
        return Arrays.stream(values())
                .filter(rc -> rc.code.equals(code))
                .findFirst()
                .orElse(ERROR);
    }

    /**
     * 获取状态码
     *
     * @return 状态码
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 获取状态消息
     *
     * @return 状态消息
     */
    public String getMsg() {
        return msg;
    }

    /**
     * 获取国际化消息键
     *
     * @return 消息键
     */
    public String getMessageKey() {
        return messageKey;
    }
}
