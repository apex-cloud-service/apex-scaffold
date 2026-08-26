package com.apex.scaffold.infrastructure.chuanglan.service;

import com.apex.scaffold.common.util.JsonUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 创蓝请求随机数、模板参数与签名构建器。
 *
 * <p>所有协议级加密和规范化细节集中在本组件，API 编排类只负责选择配置、发起请求和映射响应。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
@Component
public class ChuanglanRequestSigner {

    /** 安全随机数生成器。 */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成短信 API 鉴权签名。
     *
     * @param password API 密码
     * @param nonce 随机数
     * @param timestamp 秒级时间戳
     * @return 十六进制签名
     */
    public String signSms(String password, String nonce, String timestamp) {
        String passwordDigest = md5(password);
        String[] values = {passwordDigest, nonce, timestamp};
        java.util.Arrays.sort(values);
        return hmacSha256(values[0] + values[1] + values[2], passwordDigest);
    }

    /**
     * 生成闪验参数签名。
     *
     * @param params 不包含 sign 的请求参数
     * @param appKey 应用密钥
     * @return HmacSHA256 十六进制签名
     */
    public String signFlash(Map<String, String> params, String appKey) {
        StringBuilder canonical = new StringBuilder();
        new TreeMap<>(params).forEach((key, value) -> canonical.append(key).append(value));
        return hmacSha256(canonical.toString(), appKey);
    }

    /**
     * 构建短信模板参数 JSON。
     *
     * @param code 验证码
     * @return 创蓝模板参数 JSON
     */
    public String buildTemplateParams(String code) {
        Map<String, String> parameter = new LinkedHashMap<>();
        parameter.put("param1", code);
        return JsonUtil.toJson(Collections.singletonList(parameter));
    }

    /**
     * 生成 32 位十六进制随机数。
     *
     * @return nonce
     */
    public String nonce() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return toHex(bytes);
    }

    /** 计算 HmacSHA256。 */
    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("创蓝签名生成失败", exception);
        }
    }

    /** 计算 MD5 协议摘要。 */
    private String md5(String data) {
        try {
            return toHex(MessageDigest.getInstance("MD5").digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JRE 不支持 MD5", exception);
        }
    }

    /** 将字节数组转换为小写十六进制。 */
    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
