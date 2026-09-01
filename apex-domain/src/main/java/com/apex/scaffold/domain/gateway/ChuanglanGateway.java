package com.apex.scaffold.domain.gateway;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * 创蓝智能云平台网关接口
 *
 * <p>定义与创蓝253云通信平台交互的核心接口方法，作为领域层的出站端口（Port）。
 * 该接口遵循 DDD 中的 Gateway 模式，将基础设施层的具体实现与领域层解耦，
 * application 层通过该接口调用创蓝平台能力，不依赖具体实现。
 *
 * <h2>支持的能力</h2>
 * <ul>
 *   <li>一键登录（闪验）：通过客户端SDK获取的token置换用户手机号</li>
 *   <li>短信发送：发送验证码/通知类短信到指定手机号</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>用户使用创蓝一键登录时，App端传递闪验token，服务端通过此接口获取手机号</li>
 *   <li>用户选择短信验证码登录时，服务端通过此接口发送验证码短信</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ChuanglanGateway {

    /**
     * 一键登录 - 通过闪验token获取手机号（简化版）
     *
     * <p>App端集成创蓝闪验SDK，用户点击一键登录后SDK返回临时token，
     * 服务端使用该token调用创蓝闪验服务端API，置换用户的真实手机号码。
     *
     * <h3>业务流程</h3>
     * <ol>
     *   <li>App端集成创蓝闪验SDK，初始化后预取号</li>
     *   <li>用户点击"一键登录"，SDK返回临时token</li>
     *   <li>App将token传递给服务端登录接口</li>
     *   <li>服务端调用此方法，向创蓝闪验API请求置换手机号</li>
     *   <li>获取手机号后完成登录流程</li>
     * </ol>
     *
     * @param token 创蓝闪验SDK返回的临时令牌，由App端传递
     * @return 包含手机号的响应结果Mono，成功时可通过 result.getPhone() 获取手机号
     * @see #getPhoneNumberByToken(String, String, String, String)
     */
    Mono<PhoneNumberResult> getPhoneNumberByToken(String token);

    /**
     * 一键登录 - 通过闪验token获取手机号（完整版）
     *
     * <p>调用创蓝闪验服务端API，使用客户端SDK获取的临时token置换用户的真实手机号码。
     * 该接口需要在创蓝闪验控制台配置服务端出口IP白名单以确保安全性。
     *
     * <h3>接口规范（创蓝闪验服务端文档V3.X）</h3>
     * <ul>
     *   <li>协议：HTTPS</li>
     *   <li>请求方式：POST</li>
     *   <li>请求内容类型：application/x-www-form-urlencoded</li>
     *   <li>响应内容类型：application/json</li>
     *   <li>接口地址：https://wsflash.253.com/open/flashsdk/mobile-query</li>
     * </ul>
     *
     * <h3>请求参数</h3>
     * <table border="1">
     *   <tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>token</td><td>String</td><td>是</td><td>运营商token，SDK返回，一次有效</td></tr>
     *   <tr><td>clientIp</td><td>String</td><td>否</td><td>客户端IP，用于反欺诈核验</td></tr>
     *   <tr><td>encryptType</td><td>String</td><td>否</td><td>加密方式：0(AES)、1(RSA)，默认0</td></tr>
     *   <tr><td>outId</td><td>String</td><td>否</td><td>客户方流水号</td></tr>
     * </table>
     *
     * @param token 创蓝闪验SDK返回的临时令牌
     * @param clientIp 客户端IP地址，用于反欺诈核验（可选）
     * @param encryptType 手机号加密方式：0（AES，默认）、1（RSA）
     * @param outId 客户方流水号（可选）
     * @return 包含手机号的响应结果Mono
     * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
     */
    Mono<PhoneNumberResult> getPhoneNumberByToken(String token, String clientIp, String encryptType, String outId);

    /**
     * 一键登录 - 通过闪验token获取手机号（多平台版）。
     *
     * <p>P0-CL-9修复：根据创蓝文档「一个应用对应一个appid」的要求，
     * 不同平台需使用各自的 AppID/AppKey 进行闪验 token 置换。</p>
     *
     * @param token 创蓝闪验SDK返回的临时令牌
     * @param clientIp 客户端IP地址（可选）
     * @param encryptType 加密方式：0（AES）、1（RSA）
     * @param outId 客户方流水号（可选）
     * @param platform 平台标识（android/ios/h5/harmony/wechat），为null时默认android
     * @return 包含手机号的响应结果Mono
     * * @since 3.1.0 */
    Mono<PhoneNumberResult> getPhoneNumberByToken(
            String token, String clientIp, String encryptType, String outId, String platform);

    /**
     * 发送短信（v2模板API）
     *
     * <p>通过创蓝253短信平台v2模板API发送短信到指定手机号。
     * 使用模板ID方式发送，验证码作为模板参数填充到模板中。
     *
     * <h3>发送流程</h3>
     * <ol>
     *   <li>生成验证码并通过模板参数JSON传递给创蓝v2 API</li>
     *   <li>创蓝根据模板ID将验证码填充到模板中发送给用户</li>
     * </ol>
     *
     * @param phone 目标手机号，中国大陆11位手机号码
     * @param code 短信验证码，6位数字，将作为模板变量 param1 填充到短信模板中
     * @return 发送结果Mono，包含msgId和状态码
     * @see <a href="https://doc.chuanglan.com/document/HAQYSZKH9HT5Z50L">创蓝短信发送API文档</a>
     */
    Mono<SmsSendResult> sendSms(String phone, String code);

    /**
     * 查询创蓝账户余额
     *
     * <p>查询当前创蓝253云通信平台账户的短信剩余条数或余额。
     * 建议在短信发送前或定时任务中调用，确保账户余额充足。
     *
     * @return 余额信息Mono，包含余额数值和单位
     */
    Mono<BalanceResult> queryBalance();

    /**
     * 本机号校验 - 校验当前流量卡手机号与传入手机号是否一致
     *
     * <p>调用创蓝闪验服务端API，校验当前流量卡的手机号与传入的手机号码是否一致。
     *
     * <h3>接口规范（创蓝闪验服务端文档V3.X）</h3>
     * <ul>
     *   <li>协议：HTTPS</li>
     *   <li>请求方式：POST</li>
     *   <li>请求内容类型：application/x-www-form-urlencoded</li>
     *   <li>响应内容类型：application/json</li>
     *   <li>接口地址：https://wsflash.253.com/open/flashsdk/mobile-validate</li>
     * </ul>
     *
     * <h3>请求参数</h3>
     * <table border="1">
     *   <tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>token</td><td>String</td><td>是</td><td>运营商token，SDK返回</td></tr>
     *   <tr><td>mobile</td><td>String</td><td>是</td><td>待校验的手机号码</td></tr>
     *   <tr><td>outId</td><td>String</td><td>否</td><td>客户方流水号</td></tr>
     * </table>
     *
     * <h3>响应格式</h3>
     * <pre>{@code * {
     *   "code": "200000",
     *   "message": "成功",
     *   "chargeStatus": 1,
     *   "data": {
     *     "tradeNo": "18112115031414011",
     *     "isVerify": "1"
     *   }
     * }
     * }</pre>
     *
     * @param token 本机认证SDK返回的临时令牌
     * @param mobile 待校验的手机号码
     * @param outId 客户方流水号（可选）
     * @return 校验结果Mono，isVerify为"1"表示是本机号码，"0"表示非本机号码
     * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
     */
    Mono<MobileValidateResult> validateMobile(String token, String mobile, String outId);

    /**
     * 本机号校验（简化版）
     *
     * <p>简化版本，仅传入token和mobile，其他参数使用默认值。
     *
     * @param token 本机认证SDK返回的临时令牌
     * @param mobile 待校验的手机号码
     * @return 校验结果Mono
     * @see #validateMobile(String, String, String)
     */
    Mono<MobileValidateResult> validateMobile(String token, String mobile);

    /**
     * 一键登录手机号置换结果
     *
     * <p>封装创蓝闪验API返回的手机号置换结果。通过闪验token成功置换手机号时，
     * {@code success} 为true且 {@code phone} 为有效手机号；失败时
     * {@code success} 为false，可通过 {@code errorCode} 和 {@code errorMessage} 获取错误详情。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class PhoneNumberResult {

        /** 手机号，中国大陆11位数字格式 */
        private String phone;

        /** 是否成功获取手机号 */
        private boolean success;

        /** 错误码，成功时为null */
        private String errorCode;

        /** 错误描述，成功时为null */
        private String errorMessage;

        /**
         * 创建 {@code PhoneNumberResult} 实例。
         * @param phone   手机号，中国大陆11位数字格式
         * @param success 是否成功获取手机号
         */
        public PhoneNumberResult(String phone, boolean success) {
            this.phone = phone;
            this.success = success;
        }
    }

    /**
     * 短信发送结果
     *
     * <p>封装创蓝253短信平台发送短信后的响应结果。发送成功时 {@code msgId} 为
     * 创蓝平台返回的唯一消息标识，{@code success} 为true；失败时可通过
     * {@code errorCode} 和 {@code errorMessage} 获取失败原因。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class SmsSendResult {

        /** 消息ID，发送成功时创蓝平台返回的唯一标识 */
        private String msgId;

        /** 是否发送成功 */
        private boolean success;

        /** 错误码，成功时为null */
        private String errorCode;

        /** 错误描述，成功时为null */
        private String errorMessage;

        /**
         * 创建 {@code SmsSendResult} 实例。
         * @param msgId   创蓝平台返回的消息标识
         * @param success 是否发送成功
         */
        public SmsSendResult(String msgId, boolean success) {
            this.msgId = msgId;
            this.success = success;
        }
    }

    /**
     * 本机号校验结果
     *
     * <p>封装创蓝闪验本机号校验API的响应结果。校验成功时 {@code isVerify} 为"1"表示是本机号码，
     * "0"表示非本机号码；失败时可通过 {@code errorCode} 和 {@code errorMessage} 获取错误详情。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class MobileValidateResult {

        /** 校验结果："1"表示是本机号码，"0"表示非本机号码 */
        private String isVerify;

        /** 是否校验成功 */
        private boolean success;

        /** 交易流水号 */
        private String tradeNo;

        /** 错误码，成功时为null */
        private String errorCode;

        /** 错误描述，成功时为null */
        private String errorMessage;

        /**
         * 判断是否为本机号码
         *
         * @return true表示是本机号码，false表示非本机号码或校验失败
         */
        public boolean isNativeMobile() {
            return success && "1".equals(isVerify);
        }
    }

    /**
     * 账户余额查询结果
     *
     * <p>封装创蓝253平台账户余额查询的响应结果。查询成功时 {@code balance} 包含
     * 余额数值且 {@code success} 为true；失败时可通过 {@code errorCode} 和
     * {@code errorMessage} 获取错误详情。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class BalanceResult {

        /** 余额数值 */
        private String balance;

        /** 是否查询成功 */
        private boolean success;

        /** 错误码，成功时为null */
        private String errorCode;

        /** 错误描述，成功时为null */
        private String errorMessage;

        /**
         * 创建 {@code BalanceResult} 实例。
         * @param balance 账号余额
         * @param success 是否查询成功
         */
        public BalanceResult(String balance, boolean success) {
            this.balance = balance;
            this.success = success;
        }
    }
}
