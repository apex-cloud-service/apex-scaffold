package com.apex.scaffold.infrastructure.chuanglan.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 创蓝智能云平台配置属性
 *
 * <p>用于配置创蓝253云通信平台的相关连接参数，包括短信服务API和闪验（一键登录）服务API。
 * 所有敏感信息通过环境变量注入，确保生产环境配置安全。
 *
 * <h2>配置结构</h2>
 * <ul>
 *   <li>{@code chuanglan.sms} - 短信服务相关配置（接口地址、账号、密码、签名、模板ID）</li>
 *   <li>{@code chuanglan.flash} - 闪验（一键登录）服务相关配置（接口地址、appId、appKey）</li>
 * </ul>
 *
 * <h2>配置示例（YAML格式）</h2>
 * <pre>{@code * chuanglan:
 *   # 短信服务配置（v2模板API）
 *   sms:
 *     host: smssh.253.com           # 创蓝短信API主机地址
 *     port: 443                     # 端口，使用HTTPS时默认443
 *     account: ${CL_SMS_ACCOUNT}    # 账号，从环境变量读取
 *     password: ${CL_SMS_PASSWORD}  # 密码，从环境变量读取
 *     signature: "【Apex】"          # 短信签名
 *     template-id: 1234567          # 短信模板ID，从创蓝控制台获取
 *     ssl-enabled: true             # v2 API推荐使用HTTPS，默认true
 *
 *   # 闪验服务配置（创蓝闪验SDK服务端）
 *   flash:
 *     host: wsflash.253.com         # 创蓝闪验API主机地址
 *     port: 443                    # 端口，默认443
 *     app-id: ${CL_FLASH_APP_ID}   # App ID，从环境变量读取
 *     app-key: ${CL_FLASH_APP_KEY}  # App Key，从环境变量读取
 *     ssl-enabled: true             # 是否启用HTTPS，默认true
 * }</pre>
 *
 * <h2>环境变量说明</h2>
 * <table border="1">
 *   <tr><th>环境变量</th><th>对应配置</th><th>说明</th></tr>
 *   <tr><td>CL_SMS_HOST</td><td>chuanglan.sms.host</td><td>短信API主机</td></tr>
 *   <tr><td>CL_SMS_PORT</td><td>chuanglan.sms.port</td><td>短信API端口</td></tr>
 *   <tr><td>CL_SMS_ACCOUNT</td><td>chuanglan.sms.account</td><td>短信账号</td></tr>
 *   <tr><td>CL_SMS_PASSWORD</td><td>chuanglan.sms.password</td><td>短信密码</td></tr>
 *   <tr><td>CL_SMS_SIGNATURE</td><td>chuanglan.sms.signature</td><td>短信签名</td></tr>
 *   <tr><td>CL_SMS_TEMPLATE_ID</td><td>chuanglan.sms.template-id</td><td>短信模板ID</td></tr>
 *   <tr><td>CL_SMS_SSL</td><td>chuanglan.sms.ssl-enabled</td><td>短信HTTPS开关</td></tr>
 *   <tr><td>CL_FLASH_HOST</td><td>chuanglan.flash.host</td><td>闪验API主机</td></tr>
 *   <tr><td>CL_FLASH_PORT</td><td>chuanglan.flash.port</td><td>闪验API端口</td></tr>
 *   <tr><td>CL_FLASH_APP_ID</td><td>chuanglan.flash.app-id</td><td>闪验App ID</td></tr>
 *   <tr><td>CL_FLASH_APP_KEY</td><td>chuanglan.flash.app-key</td><td>闪验App Key</td></tr>
 *   <tr><td>CL_FLASH_SSL</td><td>chuanglan.flash.ssl-enabled</td><td>闪验HTTPS开关</td></tr>
 * </table>
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 * @see <a href="https://doc.chuanglan.com/document/0S0TET7P39AVZE7N">创蓝短信API文档</a>
 * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "chuanglan")
public class ChuanglanProperties {

    /**
     * 构造创蓝平台配置属性
     *
     * <p>使用默认构造器创建配置实例，Spring Boot会自动绑定配置属性。
     */
    public ChuanglanProperties() {}

    /**
     * 短信服务配置
     *
     * <p>创蓝253短信平台的连接参数，用于发送验证码和通知类短信。
     *
     * @see Sms
     */
    private Sms sms = new Sms();

    /**
     * 闪验（一键登录）服务配置
     *
     * <p>创蓝闪验SDK服务端配置，用于通过token置换用户手机号。
     * 该配置依据创蓝闪验服务端接口文档V3.X。
     *
     * <h3>多平台说明</h3>
     * <p>根据创蓝文档<a href="https://doc.chuanglan.com/document/NPQOJQE28LK8Z23U">
     * "一个应用对应一个appid，多个应用（不同bundleID）需在创蓝闪验平台创建多个应用以对应多个appid"</a>，
     * 不同平台需独立注册应用并获取各自的 AppID/AppKey。</p>
     *
     * <p>支持的平台如下，每平台独立配置对应的闪验凭证：</p>
     * <ul>
     *   <li><b>android</b> — Android APP（默认平台）</li>
     *   <li><b>ios</b> — iOS APP</li>
     *   <li><b>h5</b> — H5 网页</li>
     *   <li><b>harmony</b> — 鸿蒙 APP</li>
     *   <li><b>wechat</b> — 微信小程序插件</li>
     * </ul>
     *
     * <p>当前仅 Android 平台有已配置的凭证，其余平台待分配后填写。</p>
     *
     * @see FlashPlatform
     * @see Flash
     * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
     */
    private Flash flash = new Flash();

    /**
     * 短信服务配置
     *
     * <p>创蓝253短信平台的连接参数，用于发送验证码和通知类短信。
     *
     * <h3>接口信息（v2模板API）</h3>
     * <ul>
     *   <li><b>短信发送</b>：POST /msg/sms/v2/tpl/send</li>
     *   <li><b>余额查询</b>：POST /msg/balance/json</li>
     *   <li><b>默认地址</b>：smssh.253.com:443</li>
     *   <li><b>鉴权方式</b>：HmacSHA256 签名，通过 X-QA-Hmac-Signature 请求头传递</li>
     * </ul>
     *
     * @author Apex Team
     * @see <a href="https://doc.chuanglan.com/document/0S0TET7P39AVZE7N">创蓝短信API文档</a>
     */
    @Data
    public static class Sms {

        /**
         * 构造短信服务配置
         *
         * <p>使用默认构造器创建配置实例。
         */
        public Sms() {}

        /**
         * 创蓝短信API主机地址
         *
         * <p>短信API服务的主机域名，v2模板API默认为 smssh.253.com。
         *
         * @see <a href="https://doc.chuanglan.com/document/0S0TET7P39AVZE7N">创蓝短信API文档</a>
         */
        private String host = "smssh.253.com";

        /**
         * 创蓝短信API端口
         *
         * <p>短信API服务的端口号，v2模板API默认使用HTTPS，端口443。
         *
         * @see #sslEnabled
         */
        private int port = 443;

        /**
         * 创蓝短信API账号
         *
         * <p>在创蓝平台申请的短信API账号，用于身份认证。
         * 建议通过环境变量 CL_SMS_ACCOUNT 注入。
         */
        private String account;

        /**
         * 创蓝短信API密码
         *
         * <p>在创蓝平台申请的短信API密码，用于身份认证。
         * 建议通过环境变量 CL_SMS_PASSWORD 注入。
         * <p><b>注意</b>：生产环境中请勿将密码硬编码在配置文件中。
         */
        private String password;

        /**
         * 短信签名
         *
         * <p>短信内容的签名标识，格式为【签名】，如【Apex】。
         * 签名必须在创蓝平台报备审核通过后才能使用。
         *
         * @see <a href="https://doc.chuanglan.com/document/0S0TET7P39AVZE7N">创蓝短信签名说明</a>
         */
        private String signature;

        /**
         * 短信发送接口URI
         *
         * <p>短信发送API的接口路径，v2模板API固定值为 /msg/sms/v2/tpl/send。
         * 使用创蓝模板ID方式发送，通过 HmacSHA256 签名进行鉴权。
         * 此值通常不需要修改。
         *
         * @see <a href="https://doc.chuanglan.com/document/HAQYSZKH9HT5Z50L">创蓝短信发送API文档</a>
         */
        private String sendUri = "/msg/sms/v2/tpl/send";

        /**
         * 模板ID
         *
         * <p>创蓝短信模板ID，模板跟单个API账号为绑定关系。
         * 可通过模板列表接口查询或登录创蓝控制台"模板管理"查看。
         * v2 API必须使用模板ID发送，不再支持直接传短信内容。
         *
         * @see <a href="https://doc.chuanglan.com/document/GMX1K8UDQRZKPDCT">创蓝模板管理API文档</a>
         */
        private String templateId;

        /**
         * 余额查询接口URI
         *
         * <p>余额查询API的接口路径，固定值为 /msg/balance/json。
         * 此值通常不需要修改。
         */
        private String balanceUri = "/msg/balance/json";

        /**
         * 是否启用HTTPS
         *
         * <p>控制是否使用HTTPS协议访问短信API。
         * v2模板API推荐使用HTTPS，默认值为 true。
         */
        private boolean sslEnabled = true;

        // 手动 getter（Lombok @Data 在某些场景不生成静态内部类方法）
        /**
         * 获取Host
         *
         * @return 结果
         */
        public String getHost() {
            return host;
        }

        /**
         * 获取Port
         *
         * @return 结果
         */
        public int getPort() {
            return port;
        }

        /**
         * 计数count
         *
         * @return 结果
         */
        public String getAccount() {
            return account;
        }

        /**
         * 获取Password
         *
         * @return 结果
         */
        public String getPassword() {
            return password;
        }

        /**
         * 获取Signature
         *
         * @return 结果
         */
        public String getSignature() {
            return signature;
        }

        /**
         * 获取SendUri
         *
         * @return 结果
         */
        public String getSendUri() {
            return sendUri;
        }

        /**
         * 获取TemplateId
         *
         * @return 结果
         */
        public String getTemplateId() {
            return templateId;
        }

        /**
         * 获取BalanceUri
         *
         * @return 结果
         */
        public String getBalanceUri() {
            return balanceUri;
        }

        /**
         * isSslEnabled
         *
         * @return 结果
         */
        public boolean isSslEnabled() {
            return sslEnabled;
        }
    }

    /**
     * 闪验（一键登录）服务配置
     *
     * <p>创蓝闪验SDK服务端配置，用于通过token置换用户手机号。
     * 该配置依据创蓝闪验服务端接口文档V3.X。
     *
     * <h3>接口信息</h3>
     * <ul>
     *   <li><b>手机号置换</b>：POST /open/flashsdk/mobile-query</li>
     *   <li><b>默认地址</b>：wsflash.253.com:443</li>
     *   <li><b>协议</b>：HTTPS（强制推荐）</li>
     * </ul>
     *
     * @author Apex Team
     * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
     */
    @Data
    public static class Flash {

        /**
         * 构造闪验服务配置
         */
        public Flash() {}

        // ========== 公共配置 ==========

        /** 创蓝闪验服务端API主机地址 */
        private String host = "wsflash.253.com";

        /** 创蓝闪验服务端API端口 */
        private int port = 443;

        /** 手机号置换接口URI */
        private String tokenValidateUri = "/open/flashsdk/mobile-query";

        /** 本机号校验接口URI */
        private String mobileValidateUri = "/open/flashsdk/mobile-validate";

        /** 是否启用HTTPS */
        private boolean sslEnabled = true;

        /** 手机号加密方式（0=AES，1=RSA） */
        private String encryptType = "0";

        // 手动 getter（Lombok @Data 在某些场景不生成静态内部类方法）
        /**
         * 获取Host
         *
         * @return 结果
         */
        public String getHost() {
            return host;
        }

        /**
         * 获取Port
         *
         * @return 结果
         */
        public int getPort() {
            return port;
        }

        /**
         * 获取TokenValidateUri
         *
         * @return 结果
         */
        public String getTokenValidateUri() {
            return tokenValidateUri;
        }

        /**
         * 获取MobileValidateUri
         *
         * @return 结果
         */
        public String getMobileValidateUri() {
            return mobileValidateUri;
        }

        /**
         * isSslEnabled
         *
         * @return 结果
         */
        public boolean isSslEnabled() {
            return sslEnabled;
        }

        /**
         * 获取EncryptType
         *
         * @return 结果
         */
        public String getEncryptType() {
            return encryptType;
        }

        // ========== 各平台独立闪验凭证 ==========

        /** Android 平台闪验配置 */
        private FlashPlatform android = new FlashPlatform();

        /** iOS 平台闪验配置 */
        private FlashPlatform ios = new FlashPlatform();

        /** H5 平台闪验配置 */
        private FlashPlatform h5 = new FlashPlatform();

        /** 鸿蒙平台闪验配置 */
        private FlashPlatform harmony = new FlashPlatform();

        /** 微信小程序插件闪验配置 */
        private FlashPlatform wechat = new FlashPlatform();

        // ========== 兼容旧配置（单平台模式下的 appId/appKey，已废弃） ==========

        /** @deprecated 请使用 platform-specific 的 {{@link #getPlatformConfig(String)}} */
        @Deprecated
        private String appId;

        /** @deprecated 请使用 platform-specific 的 {{@link #getPlatformConfig(String)}} */
        @Deprecated
        private String appKey;

        /**
         * 根据平台标识获取对应的闪验平台配置。
         *
         * <p>若对应平台未配置 appId，则尝试回退到公共 appId/appKey（旧配置兼容）。
         * 平台标识通常由客户端传入，常见值：android、ios、h5、harmony、wechat。</p>
         *
         * @param platform 平台标识（大小写不敏感）
         * @return 对应平台的闪验配置，不会返回 null
         * @since 3.1.0
         */
        public FlashPlatform getPlatformConfig(String platform) {
            if (platform == null || platform.isBlank()) {
                return getDefaultPlatform();
            }
            return switch (platform.toLowerCase()) {
                case "android" -> requireConfigured(android, platform);
                case "ios" -> requireConfigured(ios, platform);
                case "h5" -> requireConfigured(h5, platform);
                case "harmony" -> requireConfigured(harmony, platform);
                case "wechat" -> requireConfigured(wechat, platform);
                default -> throw new IllegalArgumentException("不支持的闪验平台: " + platform);
            };
        }

        /**
         * 获取默认平台（Android）的闪验配置。
         *
         * <p>若 Android 平台未配置，回退到旧的公共字段。</p>
         *
         * @return 默认平台配置
         */
        private FlashPlatform getDefaultPlatform() {
            if (android.isConfigured()) {
                return android;
            }
            // 回退到旧的公共配置
            FlashPlatform fallback = new FlashPlatform();
            fallback.setAppId(appId);
            fallback.setAppKey(appKey);
            return requireConfigured(fallback, "android");
        }

        /**
         * 校验闪验平台配置是否完整，未配置时抛出异常。
         *
         * @param config   待校验的闪验平台配置
         * @param platform 平台名称（用于异常信息提示）
         * @return 校验通过的闪验平台配置
         * @throws IllegalStateException 配置为空或未配置时抛出
         */
        private FlashPlatform requireConfigured(FlashPlatform config, String platform) {
            if (config == null || !config.isConfigured()) {
                throw new IllegalStateException("闪验平台凭据未配置: " + platform);
            }
            return config;
        }
    }

    /**
     * 单平台闪验配置（对应创蓝闪验平台上的一个应用）。
     *
     * <p>根据创蓝文档要求，每个平台（Android/iOS/H5/Harmony/微信小程序）
     * 需独立注册应用并获取各自的 AppID 和 AppKey。</p>
     *
     * @author Apex Team
     * @since 3.1.0
     */
    @Data
    public static class FlashPlatform {

        /** 构造平台闪验配置 */
        public FlashPlatform() {}

        /** 创蓝闪验 App ID（该平台专属） */
        private String appId;

        /** 创蓝闪验 App Key（该平台专属，用于 HmacSHA256 签名和 AES 解密） */
        private String appKey;

        // 手动 getter/setter（Lombok @Data 在某些场景不生成静态内部类方法）
        /**
         * 获取AppId
         *
         * @return 结果
         */
        public String getAppId() {
            return appId;
        }

        /**
         * 设置AppId
         *
         * @param appId 参数
         */
        public void setAppId(String appId) {
            this.appId = appId;
        }

        /**
         * 获取AppKey
         *
         * @return 结果
         */
        public String getAppKey() {
            return appKey;
        }

        /**
         * 设置AppKey
         *
         * @param appKey 参数
         */
        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        /**
         * 判断是否{@code configured}。
         * @return 判断结果
         */
        public boolean isConfigured() {
            return appId != null && !appId.isBlank() && appKey != null && !appKey.isBlank();
        }
    }
}
